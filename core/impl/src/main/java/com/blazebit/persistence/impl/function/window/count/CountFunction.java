/*
 * Copyright 2014 - 2019 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.persistence.impl.function.window.count;

import com.blazebit.persistence.impl.function.window.AbstractWindowFunction;
import com.blazebit.persistence.spi.DbmsDialect;
import com.blazebit.persistence.spi.FunctionRenderContext;

/**
 *
 * @author Jan-Willem Gmelig Meyling
 * @author Sayra Ranjha
 * @since 1.4.0
 */
public class CountFunction extends AbstractWindowFunction {

    public static final String FUNCTION_NAME = "WINDOW_COUNT";

    public CountFunction(DbmsDialect dbmsDialect) {
        super(dbmsDialect);
    }

    @Override
    protected String getFunctionName() {
        return "COUNT";
    }

    @Override
    public Class<?> getReturnType(Class<?> firstArgumentType) {
        return Integer.class;
    }

    @Override
    protected void renderArguments(FunctionRenderContext context, WindowFunction windowFunction) {
        if (windowFunction.getArguments().isEmpty()) {
            context.addChunk("*");
        } else {
            super.renderArguments(context, windowFunction);
        }
    }
}