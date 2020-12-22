/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.fixup.processor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.FontMapper;
import org.apache.pdfbox.pdmodel.font.FontMappers;
import org.apache.pdfbox.pdmodel.font.FontMapping;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDFieldFactory;
import org.apache.pdfbox.pdmodel.interactive.form.PDVariableText;

/**
 *  Generate field entries from page level widget annotations
 *  if there AcroForm /Fields entry is empty.
 * 
 */
public class AcroFormOrphanWidgetsProcessor extends AbstractProcessor
{
    
    private static final Log LOG = LogFactory.getLog(AcroFormOrphanWidgetsProcessor.class);

    public AcroFormOrphanWidgetsProcessor(final PDDocument document)
    { 
        super(document); 
    }

    @Override
    public void process() {
        /*
         * Get the AcroForm in it's current state.
         *
         * Also note: getAcroForm() applies a default fixup which this processor
         * is part of. So keep the null parameter otherwise this will end
         * in an endless recursive call
         */
        final PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm(null);

        if (acroForm != null)
        {            
            resolveFieldsFromWidgets(acroForm);
        } 
    }

    private void resolveFieldsFromWidgets(final PDAcroForm acroForm)
    {
        final Map<String, PDField> nonTerminalFieldsMap = new HashMap<>();

        LOG.debug("rebuilding fields from widgets");

        final List<PDField> fields = new ArrayList<>();

        for (final PDPage page : document.getPages())
        {
            try
            {
                handleAnnotations(acroForm, fields, page.getAnnotations(), nonTerminalFieldsMap);
            }
            catch (IOException ioe)
            {
                LOG.debug("couldn't read annotations for page " + ioe.getMessage());
            }
        }

        acroForm.setFields(fields);

        // ensure that PDVariableText fields have the neccesary resources
        for (final PDField field : acroForm.getFieldTree())
        {
            if (field instanceof PDVariableText)
            {
                ensureFontResources(acroForm.getDefaultResources(), (PDVariableText) field);
            }
        }
    }

    private void handleAnnotations(final PDAcroForm acroForm, final List<PDField> fields, final List<PDAnnotation> annotations, final Map<String, PDField> nonTerminalFieldsMap)
    {
        final PDResources acroFormResources = acroForm.getDefaultResources();

        for (final PDAnnotation annot : annotations)
        {
            if (annot instanceof PDAnnotationWidget)
            {
                addFontFromWidget(acroFormResources, annot);

                final COSDictionary parent = annot.getCOSObject().getCOSDictionary(COSName.PARENT);
                if (parent != null)
                {
                    final PDField resolvedField = resolveNonRootField(acroForm, (PDAnnotationWidget) annot, nonTerminalFieldsMap);
                    if (resolvedField != null)
                    {
                        fields.add(resolvedField);
                    }
                }
                else
                {
                    final PDField field = PDFieldFactory.createField(acroForm, annot.getCOSObject(), null);
                    if (field != null)
                    {
                        fields.add(field);
                    }
                }
            }
        }
    }

    /*
     *  Add font resources from the widget to the AcroForm to make sure embedded fonts are being
     *  used and not added by ensureFontResources potentially using a fallback font
     */
    private void addFontFromWidget(final PDResources acroFormResources, final PDAnnotation annotation)
    {
        final PDAppearanceStream normalAppearanceStream = annotation.getNormalAppearanceStream();
        if (normalAppearanceStream != null && normalAppearanceStream.getResources() != null)    
        {
            final PDResources widgetResources = normalAppearanceStream.getResources();
            widgetResources.getFontNames().forEach(fontName -> {
                if (!fontName.getName().startsWith("+"))
                {
                    try
                    {
                        if (acroFormResources.getFont(fontName) == null)
                        {
                            acroFormResources.put(fontName, widgetResources.getFont(fontName));
                            LOG.debug("qdded font resource to AcroForm from widget for font name " + fontName.getName());
                        }
                    }
                    catch (IOException ioe)
                    {
                        LOG.debug("unable to add font to AcroForm for font name " + fontName.getName());
                    }
                }
                else
                {
                    LOG.debug("font resource for widget was a subsetted font - ignored: " + fontName.getName());
                }
            });
        }
    }

    /*
     *  Widgets having a /Parent entry are non root fields. Go up until the root node is found
     *  and handle from there.
     */
    private PDField resolveNonRootField(final PDAcroForm acroForm, final PDAnnotationWidget widget, final Map<String, PDField> nonTerminalFieldsMap)
    {
        COSDictionary parent = widget.getCOSObject().getCOSDictionary(COSName.PARENT);
        while (parent.containsKey(COSName.PARENT))
        {
            parent = parent.getCOSDictionary(COSName.PARENT);
            if (parent == null)
            {
                return null;
            }
        }
        
        if (nonTerminalFieldsMap.get(parent.getString(COSName.T)) == null)
        {
            final PDField field = PDFieldFactory.createField(acroForm, parent, null);
            nonTerminalFieldsMap.put(field.getFullyQualifiedName(),field);

            return field;
        }

        // this should not happen
        return null;
    }


    /*
     *  Lookup the font used in the default appearance and if this is 
     *  not available try to find a suitable font and use that.
     *  This may not be the original font but a similar font replacement
     * 
     *  TODO: implement a font lookup similar as discussed in PDFBOX-2661 so that already existing
     *        font resources might be accepatble.
     *        In such case this must be implemented in PDDefaultAppearanceString too!
     */
    private void ensureFontResources(final PDResources defaultResources, final PDVariableText field)
    {
        final String daString = field.getDefaultAppearance();
        if (daString.startsWith("/") && daString.length() > 1)
        {
            final COSName fontName = COSName.getPDFName(daString.substring(1, daString.indexOf(" ")));
            try{
                if (defaultResources != null && defaultResources.getFont(fontName) == null)
                {
                    LOG.debug("trying to add missing font resource for field " + field.getFullyQualifiedName());
                    final FontMapper mapper = FontMappers.instance();
                    final FontMapping<TrueTypeFont> fontMapping = mapper.getTrueTypeFont(fontName.getName() , null);
                    if (fontMapping != null)
                    {
                        final PDType0Font pdFont = PDType0Font.load(document, fontMapping.getFont(), false);
                        LOG.debug("looked up font for " + fontName.getName() + " - found " + fontMapping.getFont().getName());
                        defaultResources.put(fontName, pdFont);
                    }
                    else
                    {
                        LOG.debug("no suitable font found for field " + field.getFullyQualifiedName() + " for font name " + fontName.getName());
                    }
                }
            }
            catch (IOException ioe)
            {
                LOG.debug("unable to handle font resources for field " + field.getFullyQualifiedName() + ": " + ioe.getMessage());
            }
        }
    }
} 