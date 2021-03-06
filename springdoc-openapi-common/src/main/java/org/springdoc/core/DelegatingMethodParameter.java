package org.springdoc.core;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.swagger.v3.oas.annotations.Parameter;
import org.apache.commons.lang3.ArrayUtils;
import org.springdoc.api.annotations.ParameterObject;
import org.springdoc.core.converters.AdditionalModelsConverter;

import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * @author zarebski.m
 */
class DelegatingMethodParameter extends MethodParameter {
	private MethodParameter delegate;

	private Annotation[] additionalParameterAnnotations;

	private String parameterName;

	DelegatingMethodParameter(MethodParameter delegate, String parameterName, Annotation[] additionalParameterAnnotations) {
		super(delegate);
		this.delegate = delegate;
		this.additionalParameterAnnotations = additionalParameterAnnotations;
		this.parameterName = parameterName;
	}

	public static MethodParameter[] customize(String[] pNames, MethodParameter[] parameters) {
		List<MethodParameter> explodedParameters = new ArrayList<>();
		for (int i = 0; i < parameters.length; ++i) {
			MethodParameter p = parameters[i];
			if (p.hasParameterAnnotation(ParameterObject.class)) {
				Class<?> paramClass = AdditionalModelsConverter.getReplacement(p.getParameterType());
				Stream.of(paramClass.getDeclaredFields())
						.map(f -> fromGetterOfField(paramClass, f))
						.filter(Objects::nonNull)
						.forEach(explodedParameters::add);
			}
			else {
				String name = pNames != null ? pNames[i] : p.getParameterName();
				explodedParameters.add(new DelegatingMethodParameter(p, name, null));
			}
		}
		return explodedParameters.toArray(new MethodParameter[0]);
	}

	@Override
	@NonNull
	public Annotation[] getParameterAnnotations() {
		return ArrayUtils.addAll(delegate.getParameterAnnotations(), additionalParameterAnnotations);
	}

	@Override
	public String getParameterName() {
		return parameterName;
	}

	@Override
	public Method getMethod() {
		return delegate.getMethod();
	}

	@Override
	public Constructor<?> getConstructor() {
		return delegate.getConstructor();
	}

	@Override
	public Class<?> getDeclaringClass() {
		return delegate.getDeclaringClass();
	}

	@Override
	public Member getMember() {
		return delegate.getMember();
	}

	@Override
	public AnnotatedElement getAnnotatedElement() {
		return delegate.getAnnotatedElement();
	}

	@Override
	public Executable getExecutable() {
		return delegate.getExecutable();
	}

	@Override
	public MethodParameter withContainingClass(Class<?> containingClass) {
		return delegate.withContainingClass(containingClass);
	}

	@Override
	public Class<?> getContainingClass() {
		return delegate.getContainingClass();
	}

	@Override
	public Class<?> getParameterType() {
		return delegate.getParameterType();
	}

	@Override
	public Type getGenericParameterType() {
		return delegate.getGenericParameterType();
	}

	@Override
	public Class<?> getNestedParameterType() {
		return delegate.getNestedParameterType();
	}

	@Override
	public Type getNestedGenericParameterType() {
		return delegate.getNestedGenericParameterType();
	}

	@Override
	public void initParameterNameDiscovery(ParameterNameDiscoverer parameterNameDiscoverer) {
		delegate.initParameterNameDiscovery(parameterNameDiscoverer);
	}

	@Nullable
	static MethodParameter fromGetterOfField(Class<?> paramClass, Field field) {
		try {
			Annotation[] filedAnnotations = field.getDeclaredAnnotations();
			Parameter parameter = field.getAnnotation(Parameter.class);
			if (parameter != null && !parameter.required()) {
				Field fieldNullable = NullableFieldClass.class.getDeclaredField("nullableField");
				Annotation annotation = fieldNullable.getAnnotation(Nullable.class);
				filedAnnotations = ArrayUtils.add(filedAnnotations, annotation);
			}
			Annotation[] filedAnnotationsNew = filedAnnotations;
			return Stream.of(Introspector.getBeanInfo(paramClass).getPropertyDescriptors())
					.filter(d -> d.getName().equals(field.getName()))
					.map(PropertyDescriptor::getReadMethod)
					.filter(Objects::nonNull)
					.findFirst()
					.map(method -> new MethodParameter(method, -1))
					.map(param -> new DelegatingMethodParameter(param, field.getName(), filedAnnotationsNew))
					.orElse(null);
		}
		catch (IntrospectionException | NoSuchFieldException e) {
			return null;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		DelegatingMethodParameter that = (DelegatingMethodParameter) o;
		return Objects.equals(delegate, that.delegate) &&
				Arrays.equals(additionalParameterAnnotations, that.additionalParameterAnnotations) &&
				Objects.equals(parameterName, that.parameterName);
	}

	@Override
	public int hashCode() {
		int result = Objects.hash(super.hashCode(), delegate, parameterName);
		result = 31 * result + Arrays.hashCode(additionalParameterAnnotations);
		return result;
	}

	private class NullableFieldClass {
		@Nullable
		private String nullableField;
	}
}
