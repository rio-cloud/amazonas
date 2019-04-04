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

import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.services.certificatemanager.AWSCertificateManager
import com.amazonaws.services.certificatemanager.AWSCertificateManagerClientBuilder
import com.amazonaws.services.certificatemanager.model.*
import com.amazonaws.services.route53.AmazonRoute53
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder
import com.amazonaws.services.route53.model.*
import com.amazonaws.services.route53.model.ResourceRecord

class CertificateClient(
        private val acmClient: AWSCertificateManager,
        private val route53Client: AmazonRoute53,
        private val validationCheckIntervalMillis: Long = 10000,
        private val validationDNSEntryTTL: Long = 300
) {

    constructor(credentialsProvider: AWSCredentialsProvider, region: String) :
            this(
                    AWSCertificateManagerClientBuilder.standard()
                            .withRegion(region)
                            .withCredentials(credentialsProvider)
                            .build(),
                    AmazonRoute53ClientBuilder.standard()
                            .withRegion(region)
                            .withCredentials(credentialsProvider)
                            .build()
            )

    fun retrieveOrRequestCertificate(hostedZoneName: String, domainName: String): String =
            retrieveExistingCertificate(domainName) ?: requestAndValidateNewCertificate(domainName, hostedZoneName)

    private fun retrieveExistingCertificate(domainName: String): String? {
        var listCertificatesResult: ListCertificatesResult? = null
        do {
            listCertificatesResult = acmClient.listCertificates(ListCertificatesRequest().withNextToken(listCertificatesResult?.nextToken))
            val existingCertificate = listCertificatesResult.certificateSummaryList
                    .firstOrNull { it.domainName == (domainName) }
            if (existingCertificate != null) return existingCertificate.certificateArn
        } while (listCertificatesResult?.nextToken != null)
        return null
    }

    private fun requestAndValidateNewCertificate(domainName: String, hostedZoneName: String): String {
        val newCertificate = acmClient.requestCertificate(
                RequestCertificateRequest()
                        .withDomainName(domainName)
                        .withValidationMethod(ValidationMethod.DNS)
        )

        var validation: DomainValidation
        do {
            val describeCertificate = acmClient.describeCertificate(
                    DescribeCertificateRequest()
                            .withCertificateArn(newCertificate.certificateArn)
            )
            validation = describeCertificate.certificate.domainValidationOptions.first()
        } while (validation.validationStatus != "PENDING_VALIDATION")

        val resourceRecord = validation.resourceRecord
        val resourceRecordSet = ResourceRecordSet()
                .withName(resourceRecord.name)
                .withType(resourceRecord.type)
                .withTTL(validationDNSEntryTTL)
                .withResourceRecords(
                        ResourceRecord().withValue(resourceRecord.value)
                )
        val hostedZoneId = getHostedZoneId(hostedZoneName)
        route53Client.changeResourceRecordSets(
                ChangeResourceRecordSetsRequest()
                        .withHostedZoneId(hostedZoneId)
                        .withChangeBatch(
                                ChangeBatch().withChanges(
                                        Change().withAction("UPSERT")
                                                .withResourceRecordSet(resourceRecordSet)
                                )
                        )
        )
        do {
            val describeCertificate = acmClient.describeCertificate(
                    DescribeCertificateRequest()
                            .withCertificateArn(newCertificate.certificateArn)
            )
            validation = describeCertificate.certificate.domainValidationOptions.first()
            println("Waiting for certificate to be validated. Current status: ${validation.validationStatus}")
            Thread.sleep(validationCheckIntervalMillis)
        } while (validation.validationStatus != "SUCCESS")

        return newCertificate.certificateArn
    }

    private fun getHostedZoneId(hostedZoneName: String): String? {
        var listHostedZonesResult: ListHostedZonesResult? = null
        do {
            listHostedZonesResult = route53Client.listHostedZones(ListHostedZonesRequest().withMarker(listHostedZonesResult?.marker))
            val hostedZone = listHostedZonesResult.hostedZones
                    .firstOrNull { it.name == ("$hostedZoneName.") }
            if (hostedZone != null) return hostedZone.id
        } while (listHostedZonesResult?.marker != null)
        return null
    }
}
