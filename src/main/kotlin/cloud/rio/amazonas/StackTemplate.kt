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

class StackTemplate(
        val name: String,
        val templatePath: String,
        internal val parameters: List<Parameter>,
        internal val tags: List<Tag>
) {
    
    constructor(name: String, templatePath: String) : this(name, templatePath, listOf(), listOf())

    fun withParameter(parameterKey: String, parameterValue: String): StackTemplate {
        val updatedParametersList = createUpdatedParameterList(parameters, parameterKey, parameterValue)
        return StackTemplate(name, templatePath, updatedParametersList, tags)
    }

    fun withParameters(parameterMap: Map<String, String>): StackTemplate {
        var updatedParameterList = parameters
        parameterMap.forEach {
            updatedParameterList = createUpdatedParameterList(updatedParameterList, it.key, it.value)
        }
        return StackTemplate(name, templatePath, updatedParameterList, tags)
    }

    fun withTag(tagKey: String, tagValue: String): StackTemplate {
        val updatedTagList = createUpdatedTagList(tags, tagKey, tagValue)
        return StackTemplate(name, templatePath, parameters, updatedTagList)
    }

    fun withTags(tagMap: Map<String, String>): StackTemplate {
        var updatedTagList = tags
        tagMap.forEach {
            updatedTagList = createUpdatedTagList(updatedTagList, it.key, it.value)
        }
        return StackTemplate(name, templatePath, parameters, updatedTagList)
    }

    companion object {
        private fun createUpdatedParameterList(initialParameters: List<Parameter>, parameterKey: String, parameterValue: String): List<Parameter> {
            val parameters = initialParameters.toMutableList()
            parameters.forEach {
                if (it.parameterKey == parameterKey) {
                    it.parameterValue = parameterValue
                    return parameters
                }
            }
            parameters.add(Parameter().withParameterKey(parameterKey).withParameterValue(parameterValue))
            return parameters
        }
        private fun createUpdatedTagList(initialTags: List<Tag>, tagKey: String, tagValue: String): List<Tag> {
            val tags = initialTags.toMutableList()
            tags.forEach {
                if (it.key == tagKey) {
                    it.value = tagValue
                    return tags
                }
            }
            tags.add(Tag().withKey(tagKey).withValue(tagValue))
            return tags
        }
    }
}
