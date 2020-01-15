package com.andrey4623.intellij.plugins.effectiveinnerbuilder;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;

import java.util.ArrayList;
import java.util.List;

public class BuilderGenerator {

    private static final String NONNULL_ANNOTATION_QUALIFIED_NAME = "javax.annotation.Nonnull";
    private static final String NULLABLE_ANNOTATION_QUALIFIED_NAME = "javax.annotation.Nullable";

    private static final String NONNULL_ANNOTATION = "@" + NONNULL_ANNOTATION_QUALIFIED_NAME;
    private static final String NULLABLE_ANNOTATION = "@" + NULLABLE_ANNOTATION_QUALIFIED_NAME;

    private static final String PARAMETERS_ARE_NONNULL_BY_DEFAULT_ANNOTATION =
            "@javax.annotation.ParametersAreNonnullByDefault";

    private BuilderGenerator() {
    }

    public static void generate(
            PsiClass psiClass,
            boolean makeFieldsPrivateAndFinal,
            boolean ensureAllFieldsHaveAnnotations,
            boolean generateGetters,
            boolean generateBuilder,
            boolean checkForNullsInConstructor
    ) {
        if (makeFieldsPrivateAndFinal) {
            makeFieldsPrivateAndFinal(psiClass);
        }

        if (ensureAllFieldsHaveAnnotations) {
            ensureAllFieldsHaveAnnotations(psiClass);
        }

        if (generateGetters) {
            generateGetters(psiClass);
        }

        if (generateBuilder) {
            final boolean allFieldsHaveAnnotations = areAllFieldsHaveAnnotations(psiClass);
            generateBuilder(psiClass, checkForNullsInConstructor, allFieldsHaveAnnotations);
        }
    }

    private static void makeFieldsPrivateAndFinal(PsiClass psiClass) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());

        for (PsiField psiField : getFields(psiClass)) {
            final boolean fieldNonNull = isFieldNonNull(psiField);
            final boolean fieldNullable = isFieldNullable(psiField);

            if (fieldNonNull) {
                deleteNonNullAnnotation(psiField);
            }
            if (fieldNullable) {
                deleteNullableAnnotation(psiField);
            }

            psiField.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
            psiField.getModifierList().setModifierProperty(PsiModifier.PUBLIC, false);
            psiField.getModifierList().setModifierProperty(PsiModifier.PROTECTED, false);
            psiField.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);

            if (fieldNonNull) {
                addAnnotationForField(psiClass, psiField, NONNULL_ANNOTATION, elementFactory, codeStyleManager);
            }

            if (fieldNullable) {
                addAnnotationForField(psiClass, psiField, NULLABLE_ANNOTATION, elementFactory, codeStyleManager);
            }
        }
    }

    private static void addAnnotationForField(PsiClass psiClass, PsiField psiField, String annotation, PsiElementFactory elementFactory, JavaCodeStyleManager codeStyleManager) {
        PsiElement psiElement = codeStyleManager.shortenClassReferences(
                elementFactory.createAnnotationFromText(
                        annotation,
                        psiClass
                )
        );
        psiField.getModifierList().addBefore(psiElement, psiField.getModifierList().getFirstChild());
    }

    private static void ensureAllFieldsHaveAnnotations(PsiClass psiClass) {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());

        for (PsiField field : psiClass.getFields()) {
            if (
                    !field.hasModifierProperty(PsiModifier.STATIC)
                            && !isFieldNullable(field)
                            && !isFieldNonNull(field)
                            && (!(field.getType() instanceof PsiPrimitiveType))
            ) {
                addAnnotationForField(psiClass, field, NONNULL_ANNOTATION, elementFactory, codeStyleManager);
            }
        }
    }

    private static void generateGetters(PsiClass psiClass) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());

        List<PsiField> psiFields = new ArrayList<>();

        PsiField[] fields = psiClass.getFields();
        for (PsiField field : fields) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                psiFields.add(field);
            }
        }

        for (PsiField psiField : psiFields) {
            final PsiMethod method = elementFactory.createMethod(
                    "get" + makeFirstLetterUpperCase(psiField.getName()), psiField.getType()
            );

            method.getBody().add(elementFactory.createStatementFromText(
                    "return " + psiField.getName() + ";\n", psiClass
            ));

            if (!(psiField.getType() instanceof PsiPrimitiveType)) {
                if (isFieldNullable(psiField)) {
                    PsiAnnotation annotation = elementFactory.createAnnotationFromText(NULLABLE_ANNOTATION, psiClass);
                    PsiElement psiElement = codeStyleManager.shortenClassReferences(annotation);
                    method.getModifierList().addBefore(psiElement, method.getModifierList().getFirstChild());
                } else if (isFieldNonNull(psiField)) {
                    PsiAnnotation annotation = elementFactory.createAnnotationFromText(NONNULL_ANNOTATION, psiClass);
                    PsiElement psiElement = codeStyleManager.shortenClassReferences(annotation);
                    method.getModifierList().addBefore(psiElement, method.getModifierList().getFirstChild());
                }
            }

            psiClass.add(method);
        }
    }

    private static boolean areAllFieldsHaveAnnotations(PsiClass psiClass) {
        for (PsiField field : psiClass.getFields()) {
            if (
                    !field.hasModifierProperty(PsiModifier.STATIC)
                            && !isFieldNullable(field)
                            && !isFieldNonNull(field)
                            && (!(field.getType() instanceof PsiPrimitiveType))
            ) {
                return false;
            }
        }
        return true;
    }

    private static void generateBuilder(
            PsiClass psiClass,
            boolean checkForNullsInConstructor,
            boolean allFieldsHaveAnnotations
    ) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(psiClass.getProject());

        List<PsiField> psiFields = getFields(psiClass);

        final PsiClass builderClass = createBuilderClass(
                checkForNullsInConstructor,
                allFieldsHaveAnnotations,
                psiClass,
                psiFields,
                elementFactory,
                codeStyleManager
        );
        psiClass.add(builderClass);

        psiClass.add(
                createConstructor(
                        checkForNullsInConstructor, psiClass, psiFields, builderClass, elementFactory, codeStyleManager
                )
        );

        if (allFieldsHaveAnnotations) {
            createParametersAreNonNullByDefaultAnnotation(psiClass, elementFactory, codeStyleManager);
        }
    }

    private static List<PsiField> getFields(PsiClass psiClass) {
        List<PsiField> psiFields = new ArrayList<>();

        for (PsiField field : psiClass.getFields()) {
            if (!field.hasModifierProperty(PsiModifier.STATIC)) {
                psiFields.add(field);
            }
        }
        return psiFields;
    }

    private static PsiClass createBuilderClass(
            boolean checkForNullsInConstructor,
            boolean allFieldsHaveAnnotations,
            PsiClass psiClass,
            List<PsiField> psiFields,
            PsiElementFactory elementFactory,
            JavaCodeStyleManager codeStyleManager
    ) {
        final PsiClass builderClass = elementFactory.createClass("Builder");

        builderClass.getModifierList().add(elementFactory.createKeyword("static"));

        PsiMethod constructor = elementFactory.createConstructor();
        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, false);
        constructor.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
        builderClass.add(constructor);

        PsiMethod newBuilderStaticMethod = elementFactory.createMethod("builder", elementFactory.createType(builderClass));
        newBuilderStaticMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
        newBuilderStaticMethod.getBody().add(
                elementFactory.createStatementFromText(
                        "return new " + elementFactory.createType(builderClass).getName() + "();\n", psiClass
                )
        );
        psiClass.add(newBuilderStaticMethod);

        for (PsiField psiField : psiFields) {
            PsiType type = psiField.getType();

            if (checkForNullsInConstructor) {
                if (type instanceof PsiPrimitiveType) {
                    type = ((PsiPrimitiveType) type).getBoxedType(builderClass);
                }
            }

            builderClass.add(elementFactory.createField(psiField.getName(), type));

            PsiMethod method = elementFactory.createMethod(
                    "set" + makeFirstLetterUpperCase(psiField.getName()),
                    elementFactory.createType(builderClass)
            );

            PsiParameter parameter = elementFactory.createParameter(psiField.getName(), psiField.getType());
            if (isFieldNullable(psiField)) {
                PsiAnnotation annotationFromText = elementFactory.createAnnotationFromText(NULLABLE_ANNOTATION, builderClass);
                PsiElement psiElement = codeStyleManager.shortenClassReferences(annotationFromText);
                parameter.getModifierList().addBefore(psiElement, parameter.getModifierList().getFirstChild());
            }
            if (!allFieldsHaveAnnotations) {
                if (isFieldNonNull(psiField)) {
                    PsiAnnotation annotationFromText = elementFactory.createAnnotationFromText(NONNULL_ANNOTATION, builderClass);
                    PsiElement psiElement = codeStyleManager.shortenClassReferences(annotationFromText);
                    parameter.getModifierList().addBefore(psiElement, parameter.getModifierList().getFirstChild());
                }
            }
            method.getParameterList().add(parameter);

            method.getBody().add(
                    elementFactory.createStatementFromText(
                            "this." + psiField.getName() + " = " + psiField.getName() + ";\n",
                            builderClass
                    )
            );
            method.getBody().add(
                    elementFactory.createStatementFromText(
                            "return this;\n",
                            builderClass
                    )
            );

            builderClass.add(method);
        }


        final String className = makeFirstLetterLowerCase(elementFactory.createType(psiClass).getName());
        PsiMethod of = elementFactory.createMethod("of", elementFactory.createType(builderClass));
        of.getParameterList().add(elementFactory.createParameter(className, elementFactory.createType(psiClass)));
        for (PsiField psiField : psiFields) {
            of.getBody().add(
                    elementFactory.createStatementFromText(
                            "this." + psiField.getName() + "  = " + className + "." + psiField.getName() + ";\n",
                            builderClass
                    )
            );
        }
        of.getBody().add(
                elementFactory.createStatementFromText(
                        "return this;\n",
                        builderClass
                )
        );
        builderClass.add(of);


        PsiMethod buildMethod = elementFactory.createMethod("build", elementFactory.createType(psiClass));
        buildMethod.getBody().add(
                elementFactory.createStatementFromText(
                        "return new " + elementFactory.createType(psiClass).getName() + "(this);",
                        builderClass
                )
        );
        builderClass.add(buildMethod);

        return builderClass;
    }

    private static PsiMethod createConstructor(boolean checkForNullsInConstructor, PsiClass psiClass, List<PsiField> psiFields, PsiClass builderClass, PsiElementFactory elementFactory, JavaCodeStyleManager codeStyleManager) {
        PsiMethod constructor = elementFactory.createConstructor();

        constructor.getModifierList().setModifierProperty(PsiModifier.PUBLIC, false);
        constructor.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);

        constructor.getParameterList().add(
                elementFactory.createParameter("builder", elementFactory.createType(builderClass))
        );

        for (PsiField psiField : psiFields) {
            final boolean fieldNonNull = isFieldNonNull(psiField);

            StringBuilder sb = new StringBuilder();

            sb.append("this.");
            sb.append(psiField.getName());
            sb.append(" = ");
            if (checkForNullsInConstructor) {
                if (fieldNonNull || psiField.getType() instanceof PsiPrimitiveType) {
                    sb.append("java.util.Objects.requireNonNull(");
                }
            }

            sb.append("builder.");
            sb.append(psiField.getName());

            if (checkForNullsInConstructor) {
                if (fieldNonNull || psiField.getType() instanceof PsiPrimitiveType) {
                    sb.append(", \"");
                    sb.append(psiField.getName());
                    sb.append("\")");
                }
            }
            sb.append(";\n");

            PsiStatement statementFromText = elementFactory.createStatementFromText(
                    sb.toString(), psiClass
            );

            PsiElement psiElement = codeStyleManager.shortenClassReferences(statementFromText);

            constructor.getBody().add(psiElement);
        }
        return constructor;
    }

    private static void createParametersAreNonNullByDefaultAnnotation(PsiClass psiClass, PsiElementFactory elementFactory, JavaCodeStyleManager codeStyleManager) {
        PsiAnnotation annotation = elementFactory.createAnnotationFromText(
                PARAMETERS_ARE_NONNULL_BY_DEFAULT_ANNOTATION, psiClass
        );
        PsiElement psiElement = codeStyleManager.shortenClassReferences(annotation);
        psiClass.getModifierList().addBefore(psiElement, psiClass.getModifierList().getFirstChild());
    }

    private static boolean isFieldNonNull(PsiField psiField) {
        for (PsiAnnotation annotation : psiField.getAnnotations()) {
            if (NONNULL_ANNOTATION_QUALIFIED_NAME.equals(annotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static void deleteNonNullAnnotation(PsiField psiField) {
        for (PsiAnnotation annotation : psiField.getAnnotations()) {
            if (NONNULL_ANNOTATION_QUALIFIED_NAME.equals(annotation.getQualifiedName())) {
                annotation.delete();
            }
        }
    }

    private static void deleteNullableAnnotation(PsiField psiField) {
        for (PsiAnnotation annotation : psiField.getAnnotations()) {
            if (NULLABLE_ANNOTATION_QUALIFIED_NAME.equals(annotation.getQualifiedName())) {
                annotation.delete();
            }
        }
    }

    private static boolean isFieldNullable(PsiField psiField) {
        for (PsiAnnotation annotation : psiField.getAnnotations()) {
            if (NULLABLE_ANNOTATION_QUALIFIED_NAME.equals(annotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static String makeFirstLetterUpperCase(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static String makeFirstLetterLowerCase(String str) {
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
}
