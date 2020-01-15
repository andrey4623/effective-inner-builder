package com.andrey4623.intellij.plugins.effectiveinnerbuilder;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.PsiClass;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;

public class GeneratorOptions extends DialogWrapper {

    private JPanel panel;
    private JBCheckBox generateGettersCheckBox;
    private JBCheckBox generateBuilderCheckBox;
    private JBCheckBox useJSR305AnnotationsCheckBox;
    private JBCheckBox ensureAllFieldsHaveAnnotationsJBCheckBox;
    private JBCheckBox makeFieldsPrivateAndFinalCheckBox;

    private final LabeledComponent<JPanel> component;

    public GeneratorOptions(PsiClass psiClass) {
        super(psiClass.getProject());

        setTitle("Generate builder and getters");

        component = LabeledComponent.create(panel, "");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return component;
    }

    public boolean generateGetters() {
        return generateGettersCheckBox.isSelected();
    }

    public boolean generateBuilder() {
        return generateBuilderCheckBox.isSelected();
    }

    public boolean useJSR305Annotations() {
        return useJSR305AnnotationsCheckBox.isSelected();
    }

    public boolean ensureAllFieldsHaveAnnotations() {
        return ensureAllFieldsHaveAnnotationsJBCheckBox.isSelected();
    }

    public boolean makeFieldsPrivateAndFinal() {
        return makeFieldsPrivateAndFinalCheckBox.isSelected();
    }
}
