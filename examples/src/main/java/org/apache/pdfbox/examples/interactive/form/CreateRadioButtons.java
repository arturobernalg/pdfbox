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
package org.apache.pdfbox.examples.interactive.form;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDAppearanceContentStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;

/**
 * Example to create radio buttons.
 *
 * @author Tilman Hausherr
 */
public class CreateRadioButtons
{
    private CreateRadioButtons()
    {
    }

    public static void main(final String[] args) throws IOException
    {
        try (PDDocument document = new PDDocument())
        {
            final PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            final PDAcroForm acroForm = new PDAcroForm(document);

            // if you want to see what Adobe does, activate this, open with Adobe
            // save the file, and then open it with PDFDebugger
            
            //acroForm.setNeedAppearances(true)
            
            document.getDocumentCatalog().setAcroForm(acroForm);
            final List<String> options = Arrays.asList("a", "b", "c");
            final PDRadioButton radioButton = new PDRadioButton(acroForm);
            radioButton.setPartialName("MyRadioButton");
            radioButton.setExportValues(options);
            
            final PDAppearanceCharacteristicsDictionary appearanceCharacteristics = new PDAppearanceCharacteristicsDictionary(new COSDictionary());
            appearanceCharacteristics.setBorderColour(new PDColor(new float[] { 1, 0, 0 }, PDDeviceRGB.INSTANCE));
            appearanceCharacteristics.setBackground(new PDColor(new float[]{0, 1, 0.3f}, PDDeviceRGB.INSTANCE));
            // no caption => round
            // with caption => see checkbox example

            final List<PDAnnotationWidget> widgets = new ArrayList<>();
            for (int i = 0; i < options.size(); i++)
            {
                final PDAnnotationWidget widget = new PDAnnotationWidget();
                widget.setRectangle(new PDRectangle(30, PDRectangle.A4.getHeight() - 40 - i * 35, 30, 30));
                widget.setAppearanceCharacteristics(appearanceCharacteristics);
                final PDBorderStyleDictionary borderStyleDictionary = new PDBorderStyleDictionary();
                borderStyleDictionary.setWidth(2);
                borderStyleDictionary.setStyle(PDBorderStyleDictionary.STYLE_SOLID);
                widget.setBorderStyle(borderStyleDictionary);
                widget.setPage(page);
                
                final COSDictionary apNDict = new COSDictionary();
                apNDict.setItem(COSName.Off, createAppearanceStream(document, widget, false));
                apNDict.setItem(options.get(i), createAppearanceStream(document, widget, true));
                
                final PDAppearanceDictionary appearance = new PDAppearanceDictionary();
                final PDAppearanceEntry appearanceNEntry = new PDAppearanceEntry(apNDict);
                appearance.setNormalAppearance(appearanceNEntry);
                widget.setAppearance(appearance);
                widget.setAppearanceState("Off"); // don't forget this, or button will be invisible
                widgets.add(widget);
                page.getAnnotations().add(widget);
            }
            radioButton.setWidgets(widgets);

            acroForm.getFields().add(radioButton);

            // Set the texts
            try (PDPageContentStream contents = new PDPageContentStream(document, page))
            {
                for (int i = 0; i < options.size(); i++)
                {
                    contents.beginText();
                    contents.setFont(PDType1Font.HELVETICA, 15);
                    contents.newLineAtOffset(70, PDRectangle.A4.getHeight() - 30 - i * 35);
                    contents.showText(options.get(i));
                    contents.endText();
                }
            }
            
            radioButton.setValue("c");

            document.save("target/RadioButtonsSample.pdf");
        }
    }

    private static PDAppearanceStream createAppearanceStream(
            final PDDocument document, final PDAnnotationWidget widget, final boolean on) throws IOException
    {
        final PDRectangle rect = widget.getRectangle();
        final PDAppearanceStream onAP = new PDAppearanceStream(document);
        onAP.setBBox(new PDRectangle(rect.getWidth(), rect.getHeight()));
        try (PDAppearanceContentStream onAPCS = new PDAppearanceContentStream(onAP))
        {
            final PDAppearanceCharacteristicsDictionary appearanceCharacteristics = widget.getAppearanceCharacteristics();
            final PDColor backgroundColor = appearanceCharacteristics.getBackground();
            final PDColor borderColor = appearanceCharacteristics.getBorderColour();
            final float lineWidth = getLineWidth(widget);
            onAPCS.setBorderLine(lineWidth, widget.getBorderStyle(), widget.getBorder());
            onAPCS.setNonStrokingColor(backgroundColor);
            final float radius = Math.min(rect.getWidth() / 2, rect.getHeight() / 2);
            drawCircle(onAPCS, rect.getWidth() / 2, rect.getHeight() / 2, radius);
            onAPCS.fill();
            onAPCS.setStrokingColor(borderColor);
            drawCircle(onAPCS, rect.getWidth() / 2, rect.getHeight() / 2, radius - lineWidth / 2);
            onAPCS.stroke();
            if (on)
            {
                onAPCS.setNonStrokingColor(0f);
                drawCircle(onAPCS, rect.getWidth() / 2, rect.getHeight() / 2, (radius - lineWidth) / 2);
                onAPCS.fill();
            }
        }
        return onAP;
    }

    static float getLineWidth(final PDAnnotationWidget widget)
    {
        final PDBorderStyleDictionary bs = widget.getBorderStyle();
        if (bs != null)
        {
            return bs.getWidth();
        }
        return 1;
    }

    static void drawCircle(final PDAppearanceContentStream cs, final float x, final float y, final float r) throws IOException
    {
        // http://stackoverflow.com/a/2007782/535646
        final float magic = r * 0.551784f;
        cs.moveTo(x, y + r);
        cs.curveTo(x + magic, y + r, x + r, y + magic, x + r, y);
        cs.curveTo(x + r, y - magic, x + magic, y - r, x, y - r);
        cs.curveTo(x - magic, y - r, x - r, y - magic, x - r, y);
        cs.curveTo(x - r, y + magic, x - magic, y + r, x, y + r);
        cs.closePath();
    }
}
