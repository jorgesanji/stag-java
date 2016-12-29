package com.vimeo.stag.processor.generators;


import com.vimeo.stag.UseStag;
import com.vimeo.stag.processor.generators.model.ClassInfo;
import com.vimeo.stag.processor.utils.FileGenUtils;
import com.vimeo.stag.processor.utils.TypeUtils;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

public class ExternalAdapterInfo {

    @NotNull
    private static Set<String> sCheckedClasses = new HashSet<>();
    @NotNull
    final Element mExternalClassType;
    @NotNull
    private final TypeElement mAdapterType;
    @NotNull
    private final ExecutableElement mAdapterConstructor;

    public ExternalAdapterInfo(@NotNull Element typeElement, @NotNull TypeElement adapterTypeElement, @NotNull ExecutableElement adapterConstructor) {
        this.mExternalClassType = typeElement;
        this.mAdapterType = adapterTypeElement;
        this.mAdapterConstructor = adapterConstructor;
    }

    /**
     * Add adapters for the external models.
     *
     * @param elementUtils             elementUtils
     * @param stagFactoryGeneratedName stagFactoryGeneratedName
     * @param typeMirror               typeMirror
     * @param externalAdapterInfos     externalAdapterInfos
     */
    public static void addExternalAdapters(@NotNull Elements elementUtils, @NotNull String stagFactoryGeneratedName,
                                           @NotNull TypeMirror typeMirror, @NotNull Set<ExternalAdapterInfo> externalAdapterInfos) {
        if (!TypeUtils.isSupportedPrimitive(typeMirror.toString()) && typeMirror instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) typeMirror;
            Element typeElement = declaredType.asElement();
            UseStag useStag = null != typeElement ? typeElement.getAnnotation(UseStag.class) : null;
            /*
             * Make sure the external model is annotated with @UseStag
             */
            if (null != useStag) {
                ClassInfo classInfo = new ClassInfo(typeElement.asType());
                String classAdapterName = FileGenUtils.unescapeEscapedString(classInfo.getTypeAdapterQualifiedClassName());
                if (!sCheckedClasses.contains(classAdapterName)) {
                    sCheckedClasses.add(classAdapterName);
                    TypeElement adapterTypeElement = elementUtils.getTypeElement(classAdapterName);
                    if (null != adapterTypeElement) {
                        for (Element adapterEnclosedElement : adapterTypeElement.getEnclosedElements()) {
                            if (adapterEnclosedElement instanceof ExecutableElement) {
                                ExecutableElement executableElement = ((ExecutableElement) adapterEnclosedElement);
                                Name name = executableElement.getSimpleName();
                                if (name.contentEquals("<init>") && executableElement.getParameters().size() >= 2 && !stagFactoryGeneratedName.equals(executableElement.getParameters().get(1).asType().toString())) {
                                    ExternalAdapterInfo result = new ExternalAdapterInfo(typeElement, adapterTypeElement, executableElement);
                                    sCheckedClasses.add(classAdapterName);
                                    externalAdapterInfos.add(result);
                                }
                            }
                        }
                    }
                }
            }
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (null != typeArguments) {
                for (TypeMirror typeArgument : typeArguments) {
                    addExternalAdapters(elementUtils, stagFactoryGeneratedName, typeArgument, externalAdapterInfos);
                }
            }
        }
    }

    @NotNull
    String getInitializer(@NotNull String gsonVariableName, @NotNull String concatenatedTypeAdapters) {
        int paramsSize = mAdapterConstructor.getParameters().size();
        if (paramsSize == 2) {
            return "new " + FileGenUtils.escapeStringForCodeBlock(mAdapterType.toString()) + "(" + gsonVariableName + ", " + getFactoryInitializer() + ")";
        } else {
            return "new " + FileGenUtils.escapeStringForCodeBlock(mAdapterType.toString()) + "(" + gsonVariableName + ", " + getFactoryInitializer() + concatenatedTypeAdapters + ")";
        }
    }

    @NotNull
    String getFactoryInitializer() {
        return "new " + mAdapterConstructor.getParameters().get(1).asType() + "()";
    }
}