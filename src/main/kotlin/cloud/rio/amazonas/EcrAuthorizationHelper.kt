package cloud.rio.amazonas

import com.amazonaws.auth.AWSCredentialsProvider
import org.apache.commons.codec.binary.Base64
import java.io.UnsupportedEncodingException
import com.amazonaws.services.ecr.AmazonECR
import com.amazonaws.services.ecr.AmazonECRClientBuilder
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest
import java.nio.charset.Charset


class DockerCredentials (
    internal var username: String,
    internal var password: String
)

class EcrAuthorizationHelper(private val amazonECR: AmazonECR) {

    val dockerCredentials: DockerCredentials
        get() {
            val getAuthorizationTokenResult = amazonECR.getAuthorizationToken(GetAuthorizationTokenRequest())
            val decodedToken = Base64.decodeBase64(getAuthorizationTokenResult.authorizationData.first().authorizationToken)
            val credentials = String(decodedToken, Charset.forName("UTF-8"))
                    .split(":".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray()

            return DockerCredentials(credentials[0], credentials[1])
        }

    constructor(credentialsProvider: AWSCredentialsProvider, region: String) :
            this(AmazonECRClientBuilder.standard().withRegion(region).withCredentials(credentialsProvider).build())

}
