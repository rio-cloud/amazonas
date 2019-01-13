/*
 * Copyright 2019 TB Digital Services GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cloud.rio.amazonas

import com.amazonaws.services.cloudformation.model.Parameter
import com.amazonaws.services.cloudformation.model.Tag

class StackTemplate(val name: String, val templatePath: String) {
    internal val tags: MutableList<Tag> = ArrayList()
    internal val parameters: MutableList<Parameter> = ArrayList()

    fun withParameter(parameterKey: String, parameterValue: String): StackTemplate {
        addOrUpdateParameter(parameterKey, parameterValue)

        return this
    }

    fun withParameters(parameterMap: Map<String, String>): StackTemplate {
        parameterMap.forEach {
            addOrUpdateParameter(it.key, it.value)
        }

        return this
    }

    private fun addOrUpdateParameter(parameterKey: String, parameterValue: String) {
        parameters.forEach {
            if (it.parameterKey == parameterKey) {
                it.parameterValue = parameterValue
                return
            }

        }
        this.parameters.add(Parameter().withParameterKey(parameterKey).withParameterValue(parameterValue))
    }

    fun withTag(tagKey: String, tagValue: String): StackTemplate {
        addOrUpdateTag(tagKey, tagValue)

        return this
    }

    fun withTags(tagMap: Map<String, String>): StackTemplate {
        tagMap.forEach {
            addOrUpdateTag(it.key, it.value)
        }

        return this
    }

    private fun addOrUpdateTag(tagKey: String, tagValue: String) {
        tags.forEach {
            if (it.key == tagKey) {
                it.value = tagValue
                return
            }

        }
        this.tags.add(Tag().withKey(tagKey).withValue(tagValue))
    }

}
