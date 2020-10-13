package com.github.jacokoo.kform.spring

import com.github.jacokoo.kform.KForm
import com.github.jacokoo.kform.MapFormData
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping

class KFormArgumentResolver: HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter) =
        KForm::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        request: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? = (request.parameterMap + request.getAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        RequestAttributes.SCOPE_REQUEST
    ).let {
        @Suppress("UNCHECKED_CAST")
        it?.let { (it as Map<String, String>).mapValues { v -> arrayOf(v.value) } } ?: mapOf()
    })
        .filter { it.value.isNotEmpty() }
        .let { MapFormData(it.mapValues { v -> v.value[0] }) }
        .let { parameter.parameterType.constructors[0].newInstance(it) }

}