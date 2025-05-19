// Copyright © 2024-2025 Google, LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// All rights reserved.

package com.google.example.devportalexp.service;

import com.google.example.devportalexp.App;
import com.google.example.devportalexp.AppUtils;
import com.google.example.devportalexp.KeyUtility;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.pkcs.RSAPublicKey;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class X509CertificateService {
  private static X509CertificateService instance;

  private static final String CERT_SIGNATURE_ALGORITHM = "SHA256withRSA";
  private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

  private PrivateKey signingPrivateKey;
  private X509Certificate issuerCertificate;

  public static X509CertificateService getInstance() {
    if (instance == null) {
      instance = new X509CertificateService();
    }
    return instance;
  }

  static {
    java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
  }

  private X509CertificateService() {
    try {
      String certPemString =
          new String(
              App.getResourceAsStream("keys/issuer-cert.pem").readAllBytes(),
              StandardCharsets.UTF_8);
      issuerCertificate = KeyUtility.decodeCertificate(certPemString);

      String privateKeyPemString =
          new String(
              App.getResourceAsStream("keys/issuer-rsa-privatekey.pem").readAllBytes(),
              StandardCharsets.UTF_8);
      signingPrivateKey = KeyUtility.decodePrivateKey(privateKeyPemString, null).getPrivate();
    } catch (java.lang.Exception exc1) {
      exc1.printStackTrace();
      throw new RuntimeException("uncaught exception", exc1);
    }
  }

  public X509Certificate generateNewSignedCertificate(
      PublicKey publicKeyToSign, String subjectDN, String devEmail, String partnerOrgName)
      throws CertificateException,
          NoSuchAlgorithmException,
          InvalidKeyException,
          SignatureException,
          OperatorCreationException,
          NoSuchProviderException,
          IOException {
    Instant now = Instant.now();
    Date notBefore = Date.from(now);
    Date notAfter =
        Date.from(
            now.plusSeconds(AppUtils.daysInTheComingYear() * 24 * 60 * 60)); // Valid for one year

    BigInteger serialNumber = new BigInteger(160, new SecureRandom());
    X500Principal subject = new X500Principal(subjectDN);

    // Use information from the issuer certificate
    X500Principal issuer = new X500Principal(issuerCertificate.getSubjectX500Principal().getName());

    JcaX509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            issuer, serialNumber, notBefore, notAfter, subject, publicKeyToSign);

    // --- Basic Constraints ---
    boolean isCa = false;
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCa));

    // Set up Key Usage
    KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyAgreement);
    boolean kuIsCritical = true;
    certBuilder.addExtension(Extension.keyUsage, kuIsCritical, keyUsage);

    // --- Set up Subject Alternative Names (SANs) ---
    String nowAsYyyyMm = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneOffset.UTC).format(now);

    List<GeneralName> altNames =
        Arrays.asList(
            new GeneralName(GeneralName.rfc822Name, devEmail),
            new GeneralName(
                GeneralName.uniformResourceIdentifier,
                String.format("urn:%s.%s", nowAsYyyyMm, partnerOrgName.replaceAll(" ", "-"))));

    // Create the GeneralNames structure from the list
    GeneralNames subjectAltNames = new GeneralNames(altNames.toArray(new GeneralName[0]));

    // Add the SAN extension to the certificate builder
    boolean sanIsCritical = false;
    certBuilder.addExtension(Extension.subjectAlternativeName, sanIsCritical, subjectAltNames);

    // Add the extendedKeyUsage extension
    ExtendedKeyUsage extendedKeyUsage = new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth);
    boolean ekuIsCritical = false;
    certBuilder.addExtension(Extension.extendedKeyUsage, ekuIsCritical, extendedKeyUsage);

    // --- Sign the Certificate ---
    JcaContentSignerBuilder signerBuilder = new JcaContentSignerBuilder(CERT_SIGNATURE_ALGORITHM);
    ContentSigner contentSigner = signerBuilder.build(signingPrivateKey);

    JcaX509CertificateConverter converter = new JcaX509CertificateConverter().setProvider("BC");
    X509Certificate newCertificate = converter.getCertificate(certBuilder.build(contentSigner));

    // --- Verify the Signature. Just as a sanity check. ---
    PublicKey verificationKey = issuerCertificate.getPublicKey();
    newCertificate.verify(verificationKey);
    return newCertificate;
  }

  /**
   * Verifies constraints on the provided certificate, including (1) is not a CA, (2) is not
   * expired, (3) has the required clientAuth OID registered for extended key usage, (4) eku must
   * not include codeSigning, timeStamping, or OCSPSigning, (5) has an appropriate crypto alg, (6)
   * uses an appropriate digest value (SHA-256), (7) sufficient key strength, (8) is not
   * self-signed.
   *
   * @param certificate The certificate to check.
   * @throws IllegalArgumentException if a problem is found in the certificate.
   */
  public static void enforceClientCertificateConstraints(X509Certificate certificate)
      throws IllegalArgumentException {

    // 1. not a CA
    boolean[] keyUsage = certificate.getKeyUsage();
    if (keyUsage != null && keyUsage.length >= 6 && keyUsage[5]) {
      throw new IllegalArgumentException(
          "the certificate must not be usable for certificate signing");
    }

    // 2. expiry date
    // Here, do not call certificate.checkValidity(). It is possible the cert
    // is not yet valid, which is ok. The devleoper may want to provision this
    // cert for use in the future.
    Date expiryDate = certificate.getNotAfter();
    Date now = new Date();
    boolean isExpired = now.after(expiryDate);
    if (isExpired) {
      throw new IllegalArgumentException("the certificate is expired");
    }

    // 3. clientAuth OID in eku
    List<String> ekuOIDs = null;
    try {
      ekuOIDs = certificate.getExtendedKeyUsage();
    } catch (java.security.cert.CertificateParsingException exc1) {
      exc1.printStackTrace();
      throw new IllegalArgumentException("the certificate cannot be parsed", exc1);
    }
    if (ekuOIDs == null) {
      throw new IllegalArgumentException(
          "the certificate is missing extended Key Usage (clientAuth)");
    }
    String clientAuthOIDString =
        org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_clientAuth.toOID().toString();
    Optional<String> clientAuthOID =
        ekuOIDs.stream().filter(ekuOID -> ekuOID.equals(clientAuthOIDString)).findFirst();
    if (!clientAuthOID.isPresent()) {
      throw new IllegalArgumentException(
          "the certificate is missing extended Key Usage (clientAuth)");
    }

    // 4. Extended Key Usage extension must not include codeSigning, timeStamping, or OCSPSigning
    List<String> prohibitedOIDs =
        Arrays.asList(
            org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_OCSPSigning.toOID().toString(),
            org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_codeSigning.toOID().toString(),
            org.bouncycastle.asn1.x509.KeyPurposeId.id_kp_timeStamping.toOID().toString());
    Optional<String> prohibitedOID =
        ekuOIDs.stream().filter(ekuOID -> prohibitedOIDs.contains(ekuOID)).findFirst();
    if (prohibitedOID.isPresent()) {
      throw new IllegalArgumentException(
          String.format(
              "the certificate includes a prohibited OID for Extended Key Usage (%s)",
              prohibitedOID.get()));
    }

    // 5. crypto alg
    PublicKey publicKey = certificate.getPublicKey();
    String algorithm = publicKey.getAlgorithm();
    if (!"EC".equalsIgnoreCase(algorithm) && !"RSA".equalsIgnoreCase(algorithm)) {
      throw new IllegalArgumentException(
          String.format("the certificate uses an unsupported key type (%s)", algorithm));
    }

    // 6. key strength
    if (publicKey instanceof RSAPublicKey) {
      final RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
      int keyStrength = rsaPublicKey.getModulus().bitLength();
      if (keyStrength < 2048) {
        throw new IllegalArgumentException(
            String.format(
                "the public key within the certificate uses an insufficient key strength (%d)",
                keyStrength));
      }
    } else if (publicKey instanceof ECPublicKey) {
      final ECPublicKey ecPublicKey = (ECPublicKey) publicKey;
      final ECParameterSpec spec = ecPublicKey.getParams();
      int keyLength = (spec != null) ? spec.getOrder().bitLength() : 0;

      if (keyLength < 256) {
        throw new IllegalArgumentException(
            String.format(
                "the public key within the certificate uses an insufficient key strength (%d)",
                keyLength));
      }
    }

    // 7. digest alg
    String sigAlgName = certificate.getSigAlgName();
    if (!sigAlgName.startsWith("SHA256")
        && !sigAlgName.startsWith("SHA384")
        && !sigAlgName.startsWith("SHA512")) {
      throw new IllegalArgumentException(
          String.format(
              "the certificate uses an unsupported signature algorithm name (%s)", sigAlgName));
    }

    // 8. not self-signed
    String subjectDN = certificate.getSubjectX500Principal().toString();
    String issuerDN = certificate.getIssuerX500Principal().toString();
    if (subjectDN.equals(issuerDN)) {
      throw new IllegalArgumentException("the certificate must not be self-signed");
    }
  }
}
