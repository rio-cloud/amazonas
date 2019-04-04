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

import com.amazonaws.services.certificatemanager.AWSCertificateManager
import com.amazonaws.services.certificatemanager.model.*
import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult
import com.amazonaws.services.route53.model.HostedZone
import com.amazonaws.services.route53.model.ListHostedZonesResult
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkClass
import io.mockk.verify
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CertificateClient.retrieveOrRequestCertificate")
internal class CertificateClientTest {

    private val acmMock = mockkClass(AWSCertificateManager::class)
    private val route53Mock = mockkClass(AmazonRoute53::class)

    @BeforeEach
    fun prepareMocks() {
    }

    @AfterEach
    fun resetMocks() {
        clearAllMocks()
    }

    @Test
    fun `should return the ARN of an existing certificate`() {
        every {
            acmMock.listCertificates(any())
        } returns
                ListCertificatesResult()
                        .withCertificateSummaryList(
                                CertificateSummary()
                                        .withDomainName("the-domain")
                                        .withCertificateArn("the-arn"),
                                CertificateSummary()
                                        .withDomainName("another-domain")
                                        .withCertificateArn("another-arn")
                        )

        val certificateClient = CertificateClient(acmMock, route53Mock)
        val actualArn = certificateClient.retrieveOrRequestCertificate("the-hosted-zone.", "the-domain")

        assertEquals("the-arn", actualArn)
        verify(exactly = 0) {
            acmMock.requestCertificate(any())
        }
    }

    @Test
    fun `should create a new certificate and return its ARN if none exists yet`() {
        every {
            route53Mock.listHostedZones(any())
        } returns
                ListHostedZonesResult()
                        .withHostedZones(
                                HostedZone().withName("the-hosted-zone.").withId("hz123")
                        )
                        .withMarker(null)
        val wantedDomain = "the-domain"
        val wantedArn = "the-arn"
        every {
            acmMock.listCertificates(any())
        } returns
                ListCertificatesResult()
                        .withCertificateSummaryList(
                                CertificateSummary()
                                        .withDomainName("another-domain")
                                        .withCertificateArn("another-arn")
                        )
        every {
            acmMock.requestCertificate(match { it.domainName == wantedDomain && it.validationMethod == ValidationMethod.DNS.toString() })
        } returns
                RequestCertificateResult().withCertificateArn(wantedArn)
        var validated = false
        val validationResourceRecordName = "validation-resource-record-name"
        every {
            acmMock.describeCertificate(match { it.certificateArn == wantedArn })
        } answers {
            if (!validated) {
                DescribeCertificateResult()
                        .withCertificate(
                                CertificateDetail()
                                        .withCertificateArn(wantedArn)
                                        .withDomainName(wantedDomain)
                                        .withDomainValidationOptions(
                                                DomainValidation()
                                                        .withValidationStatus("PENDING_VALIDATION")
                                                        .withResourceRecord(
                                                                ResourceRecord().withName(validationResourceRecordName)
                                                        )
                                        )
                        )
            } else {
                DescribeCertificateResult()
                        .withCertificate(
                                CertificateDetail()
                                        .withCertificateArn(wantedArn)
                                        .withDomainName(wantedDomain)
                                        .withDomainValidationOptions(
                                                DomainValidation().withValidationStatus("SUCCESS")

                                        )
                        )
            }
        }
        every {
            route53Mock.changeResourceRecordSets(match {
                it.hostedZoneId == "hz123"
                        && it.changeBatch.changes.first().action == "UPSERT"
                        && it.changeBatch.changes.first().resourceRecordSet.name == validationResourceRecordName
            })
        } answers {
            validated = true
            ChangeResourceRecordSetsResult()
        }

        val certificateClient = CertificateClient(acmMock, route53Mock, validationCheckIntervalMillis = 1)
        val actualArn = certificateClient.retrieveOrRequestCertificate("the-hosted-zone", wantedDomain)

        assertEquals(wantedArn, actualArn)
        verify(exactly = 1) {
            acmMock.requestCertificate(any())
            route53Mock.changeResourceRecordSets(any())
        }
    }

}
