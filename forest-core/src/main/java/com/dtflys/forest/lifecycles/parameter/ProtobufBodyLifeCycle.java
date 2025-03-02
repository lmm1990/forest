package com.dtflys.forest.lifecycles.parameter;

import com.dtflys.forest.annotation.DataFile;
import com.dtflys.forest.annotation.ProtobufBody;
import com.dtflys.forest.backend.ContentType;
import com.dtflys.forest.exceptions.ForestRuntimeException;
import com.dtflys.forest.http.ForestRequest;
import com.dtflys.forest.mapping.MappingParameter;
import com.dtflys.forest.reflection.ForestMethod;
import com.dtflys.forest.reflection.MetaRequest;
import com.dtflys.forest.utils.ForestDataType;
import com.dtflys.forest.utils.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;

/**
 * Forest &#064;JSONBody注解的生命周期
 * @author gongjun[dt_flys@hotmail.com]
 * @since 1.5.0-BETA9
 */
public class ProtobufBodyLifeCycle extends AbstractBodyLifeCycle<ProtobufBody> {

    @Override
    public void onParameterInitialized(ForestMethod method, MappingParameter parameter, ProtobufBody annotation) {
        super.onParameterInitialized(method, parameter, annotation);
        MetaRequest metaRequest = method.getMetaRequest();

        String methodName = methodName(method);

        if (metaRequest == null) {
            throw new ForestRuntimeException("[Forest] method '" + methodName +
                    "' has not bind a Forest request annotation. Hence the annotation @BinaryBody cannot be bind on a parameter in this method.");
        }
        boolean hasDataFileAnn = false;
        for (Parameter param : method.getMethod().getParameters()) {
            Annotation dataFileAnn = param.getAnnotation(DataFile.class);
            if (dataFileAnn != null) {
                hasDataFileAnn = true;
                break;
            }
        }
        String contentTypeStr = metaRequest.getContentType();
        if (StringUtils.isBlank(contentTypeStr) && !hasDataFileAnn) {
            metaRequest.setContentType(ContentType.APPLICATION_X_PROTOBUF);
        }
        if (metaRequest.getBodyType() == null) {
            metaRequest.setBodyType(ForestDataType.PROTOBUF);
        }
        parameter.setTarget(MappingParameter.TARGET_BODY);
    }

    private static String methodName(ForestMethod method) {
        return method.getMethod().toGenericString();
    }

    @Override
    public boolean beforeExecute(ForestRequest request) {
        String contentType = request.getContentType();
        if (StringUtils.isBlank(contentType)) {
            request.setContentType(ContentType.APPLICATION_X_PROTOBUF);
        }
        return true;
    }

}
