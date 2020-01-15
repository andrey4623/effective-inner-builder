package com.andrey4623.intellij.plugins.effectiveinnerbuilder;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class ActionListener extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final PsiClass psiClass = getPsiClass(e);

        if (psiClass == null) {
            return;
        }

        final GeneratorOptions generatorOptions = new GeneratorOptions(psiClass);
        generatorOptions.show();

        if (generatorOptions.isOK()) {
            WriteCommandAction.runWriteCommandAction(
                    psiClass.getProject(),
                    () -> BuilderGenerator.generate(
                            psiClass,
                            generatorOptions.makeFieldsPrivateAndFinal(),
                            generatorOptions.ensureAllFieldsHaveAnnotations(),
                            generatorOptions.generateGetters(),
                            generatorOptions.generateBuilder(),
                            generatorOptions.useJSR305Annotations()
                    )
            );
        }
    }

    private static PsiClass getPsiClass(AnActionEvent e) {
        final PsiFile psiFile = e.getData(LangDataKeys.PSI_FILE);
        final Editor editor = e.getData(PlatformDataKeys.EDITOR);

        if (psiFile == null || editor == null) {
            return null;
        }

        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = psiFile.findElementAt(offset);
        return PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
}
