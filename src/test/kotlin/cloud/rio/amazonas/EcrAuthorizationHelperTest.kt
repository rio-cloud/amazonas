package cloud.rio.amazonas

import com.amazonaws.services.ecr.AmazonECR
import com.amazonaws.services.ecr.model.AuthorizationData
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("EcrAuthorizationHelper.dockerCredentials")
internal class EcrAuthorizationHelperTest {

    private val amazonECR = mockkClass(AmazonECR::class)

    @Test
    fun `should return Docker credentials`() {
        val token = "dXNlcjpwYXNzd29yZA==" // base64 encoded string "user:password"
        val expectedDockerCredentials = DockerCredentials("user", "password")
        val getAuthorizationTokenResult = GetAuthorizationTokenResult().withAuthorizationData(AuthorizationData().withAuthorizationToken(token))
        every {
            amazonECR.getAuthorizationToken(any())
        } returns getAuthorizationTokenResult

        val authorizationHelper = EcrAuthorizationHelper(amazonECR)
        val actualDockerCredentials = authorizationHelper.dockerCredentials

        verify { amazonECR.getAuthorizationToken(any()) }
        assertEquals(expectedDockerCredentials.password, actualDockerCredentials.password)
        assertEquals(expectedDockerCredentials.username, actualDockerCredentials.username)
    }
}
