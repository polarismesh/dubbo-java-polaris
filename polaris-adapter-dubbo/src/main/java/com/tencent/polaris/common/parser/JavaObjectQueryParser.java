/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.tencent.polaris.common.parser;

import com.tencent.polaris.api.utils.StringUtils;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaObjectQueryParser implements QueryParser {

    private static final Pattern ARRAY_PATTERN = Pattern.compile("^.+\\[[0-9]+\\]");

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaObjectQueryParser.class);

    private static final String PREFIX_PARAM = "param";

    private static final String PREFIX_PARAM_ARRAY = "param[";


    @Override
    public Optional<String> parse(String restKey, Object[] parameters) {
        int index = -1;
        if (restKey.startsWith(PREFIX_PARAM)) {
            index = 0;
            restKey = restKey.substring(PREFIX_PARAM.length());
        } else if (restKey.startsWith(PREFIX_PARAM_ARRAY)) {
            int endInx = restKey.indexOf(']');
            String indexValue = restKey.substring(1, endInx);
            index = Integer.parseInt(indexValue);
            restKey = restKey.substring(endInx + 1);
        } else {
            LOGGER.warn("invalid object expression for {}", restKey);
        }
        if (index == -1 || parameters.length <= index) {
            return Optional.empty();
        }
        Object targetValue = parameters[index];
        if (restKey.length() == 0) {
            if (Objects.isNull(targetValue)) {
                return Optional.empty();
            }
            return Optional.ofNullable(Objects.toString(targetValue));
        }
        // omit the starting dot
        restKey = restKey.substring(1);
        if (!StringUtils.isBlank(restKey)) {
            String[] tokens = restKey.split("\\.");
            for (String token : tokens) {
                if (null == targetValue) {
                    break;
                }
                targetValue = JavaObjectQueryParser.resolveValue(token, targetValue);
            }
        }
        if (Objects.isNull(targetValue)) {
            return Optional.empty();
        }
        return Optional.ofNullable(Objects.toString(targetValue));
    }

    private static Object resolveValue(String path, Object value) {
        String fieldName = path;
        int index = -1;
        Matcher matcher = ARRAY_PATTERN.matcher(path);
        if (matcher.matches()) {
            //array
            fieldName = path.substring(0, path.indexOf('['));
            String indexStr = path.substring(path.indexOf('[') + 1, path.lastIndexOf(']'));
            index = Integer.parseInt(indexStr);
        }
        Object objectByFieldName = null;
        if (value.getClass().isAssignableFrom(Map.class)) {
            Map<?, ?> mapValues = (Map<?, ?>) value;
            objectByFieldName = mapValues.get(fieldName);
        } else {
            try {
                objectByFieldName = getObjectByFieldName(value, fieldName);
            } catch (Exception e) {
                LOGGER.error("[POLARIS] fail to resolve field {} by class {}", fieldName,
                        value.getClass().getCanonicalName(), e);
            }
        }
        if (index < 0 || objectByFieldName == null) {
            return objectByFieldName;
        }
        Class<?> targetClazz = objectByFieldName.getClass();
        if (targetClazz.isArray()) {
            Class<?> componentType = targetClazz.getComponentType();
            if (!componentType.isPrimitive()) {
                Object[] values = (Object[]) objectByFieldName;
                if (values.length > index) {
                    return values[index];
                }
                return null;
            } else {
                return processPrimitiveArray(componentType, objectByFieldName, index);
            }
        } else if (targetClazz.isAssignableFrom(List.class)) {
            List<?> listValues = (List<?>) objectByFieldName;
            if (listValues.size() > index) {
                return listValues.get(index);
            }
            return null;
        } else if (targetClazz.isAssignableFrom(Collection.class)) {
            Collection<?> collectionValues = (Collection<?>) objectByFieldName;
            if (collectionValues.size() > index) {
                Iterator<?> iterator = collectionValues.iterator();
                Object nextValue = null;
                for (int i = 0; i < index; i++) {
                    nextValue = iterator.next();
                }
                return nextValue;
            }
        }
        return null;
    }

    private static Object processPrimitiveArray(Class<?> componentType, Object object, int index) {
        if (componentType == int.class) {
            int[] values = (int[]) object;
            if (values.length > index) {
                return values[index];
            }
            return null;
        } else if (componentType == long.class) {
            long[] values = (long[]) object;
            if (values.length > index) {
                return values[index];
            }
            return null;
        } else if (componentType == double.class) {
            double[] values = (double[]) object;
            if (values.length > index) {
                return values[index];
            }
            return null;
        } else if (componentType == short.class) {
            short[] values = (short[]) object;
            if (values.length > index) {
                return values[index];
            }
            return null;
        } else if (componentType == float.class) {
            float[] values = (float[]) object;
            if (values.length > index) {
                return values[index];
            }
            return null;
        } else if (componentType == boolean.class) {
            boolean[] values = (boolean[]) object;
            if (values.length > index) {
                return values[index];
            }
            return null;
        } else if (componentType == byte.class) {
            byte[] values = (byte[]) object;
            if (values.length > index) {
                return values[index];
            }
            return null;
        } else if (componentType == char.class) {
            char[] values = (char[]) object;
            if (values.length > index) {
                return values[index];
            }
            return null;
        }
        return null;
    }

    /**
     * 根据属性名返回对象的属性
     *
     * @param target 对象
     * @param fieldName 对象的属性名
     * @return 获取到的对象属性
     */
    private static Object getObjectByFieldName(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

}
