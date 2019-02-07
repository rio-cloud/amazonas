package cloud.rio.amazonas

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.kms.AWSKMS
import com.amazonaws.services.kms.AWSKMSClientBuilder
import com.amazonaws.services.kms.model.ListAliasesRequest
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.*
import org.apache.logging.log4j.LogManager

class SsmParameterCopier(
    private val sourceSsm: AWSSimpleSystemsManagement,
    private val targetSsm: AWSSimpleSystemsManagement,
    private val kmsClient: AWSKMS
) {
    constructor(sourceCredentialsProvider: AWSCredentialsProvider, targetCredentialsProvider: AWSCredentialsProvider, region: String) :
            this(
                    AWSSimpleSystemsManagementClientBuilder.standard()
                            .withRegion(region).withCredentials(sourceCredentialsProvider).build(),
                    AWSSimpleSystemsManagementClientBuilder.standard()
                            .withRegion(region).withCredentials(targetCredentialsProvider).build(),
                    AWSKMSClientBuilder.standard()
                            .withRegion(region).withCredentials(targetCredentialsProvider).build()
            )

    fun copyParameter(sourceName: String, targetName: String, encryptionKeyName: String = "aws/kms") {
        val getParameterResult = sourceSsm.getParameter(
                GetParameterRequest()
                        .withName(sourceName)
                        .withWithDecryption(WITH_DECRYPTION)
        )
        val parameter = getParameterResult.parameter
        LOGGER.info("Successfully read parameter \"{}\".", sourceName)

        val request = if(encryptionKeyName != "aws/kms") {
            PutParameterRequest()
                    .withName(targetName)
                    .withValue(parameter.value)
                    .withType(parameter.type)
                    .withKeyId(getKeyId(encryptionKeyName))
                    .withOverwrite(OVERWRITE)
        } else {
            PutParameterRequest()
                    .withName(targetName)
                    .withValue(parameter.value)
                    .withType(parameter.type)
                    .withOverwrite(OVERWRITE)
        }

        val putParameterResult = targetSsm.putParameter(request)
        LOGGER.info("Successfully set version {} of parameter \"{}\".", putParameterResult.getVersion(), targetName)
    }

    private fun getKeyId(keyName: String?): String? {
        if (keyName == null) {
            return null
        }

        var done = false
        val request = ListAliasesRequest()
        val alias = "alias/$keyName"

        while (!done) {
            val response = kmsClient.listAliases(request)

            for (entry in response.aliases) {
                if (entry.aliasName == alias) {
                    val keyId = entry.targetKeyId
                    LOGGER.info("Found matching key for alias \"{}\" with id \"{}\".", alias, keyId)
                    return keyId
                }
            }

            if (!response.isTruncated) {
                done = true
            } else {
                LOGGER.info("No key with matching alias found yet, make another request...")
                request.marker = response.nextMarker
            }
        }
        throw NoEncryptionKeyFoundException(String.format("No matching key found for alias \"%s\"", alias))
    }

    companion object {
        private val LOGGER = LogManager.getLogger(SsmParameterCopier::class.java)
        private const val OVERWRITE = true
        private const val WITH_DECRYPTION = true
    }

}

class NoEncryptionKeyFoundException internal constructor(message: String) : RuntimeException(message)
