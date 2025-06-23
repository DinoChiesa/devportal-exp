// Copyright © 2019-2025 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.google.example.devportalexp;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;

public class KeyUtility {
  public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
  public static final String END_CERT = "-----END CERTIFICATE-----";
  public static final String LINE_SEPARATOR = System.getProperty("line.separator");

  static {
    java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
  }

  private KeyUtility() throws Exception {}

  public static class KeyParseException extends Exception {
    private static final long serialVersionUID = 0L;

    KeyParseException(String message) {
      super(message);
    }

    KeyParseException(String message, Throwable th) {
      super(message, th);
    }
  }

  protected static KeyPair produceKeyPair(PrivateKey privateKey)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    RSAPrivateCrtKey privCrtKey = (RSAPrivateCrtKey) privateKey;
    PublicKey publicKey =
        KeyFactory.getInstance("RSA")
            .generatePublic(
                new RSAPublicKeySpec(
                    ((RSAPrivateKey) privateKey).getPrivateExponent(),
                    privCrtKey.getPublicExponent()));
    return new KeyPair(publicKey, privateKey);
  }

  public static KeyPair decodePrivateKey(String privateKeyPemString, String password)
      throws Exception {
    if (password == null) password = "";

    JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
    privateKeyPemString = reformIndents(privateKeyPemString);
    try (PEMParser pr = new PEMParser(new StringReader(privateKeyPemString))) {
      Object o = pr.readObject();

      if (o instanceof PrivateKeyInfo) {
        // eg, "openssl genpkey  -algorithm rsa -pkeyopt rsa_keygen_bits:2048 -out keypair.pem"
        PrivateKey privateKey = converter.getPrivateKey((PrivateKeyInfo) o);
        return produceKeyPair(privateKey);
      }

      if (o instanceof PKCS8EncryptedPrivateKeyInfo) {
        // produced by "openssl genpkey" or the series of commands reqd to sign an ec key
        PKCS8EncryptedPrivateKeyInfo pkcs8EncryptedPrivateKeyInfo = (PKCS8EncryptedPrivateKeyInfo) o;
        JceOpenSSLPKCS8DecryptorProviderBuilder decryptorProviderBuilder =
            new JceOpenSSLPKCS8DecryptorProviderBuilder();
        InputDecryptorProvider decryptorProvider =
            decryptorProviderBuilder.build(password.toCharArray());
        PrivateKeyInfo privateKeyInfo =
            pkcs8EncryptedPrivateKeyInfo.decryptPrivateKeyInfo(decryptorProvider);
        PrivateKey privateKey = converter.getPrivateKey(privateKeyInfo);
        return produceKeyPair(privateKey);
      }

      if (o instanceof PEMEncryptedKeyPair) {
        PEMDecryptorProvider decProv =
            new JcePEMDecryptorProviderBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(password.toCharArray());
        return converter.getKeyPair(((PEMEncryptedKeyPair) o).decryptKeyPair(decProv));
      }

      if (o instanceof PEMEncryptedKeyPair) {
        // produced by "openssl genrsa" or "openssl ec -genkey"
        PEMEncryptedKeyPair encryptedKeyPair = (PEMEncryptedKeyPair) o;
        PEMDecryptorProvider decryptorProvider =
            new JcePEMDecryptorProviderBuilder().build(password.toCharArray());
        return converter.getKeyPair(encryptedKeyPair.decryptKeyPair(decryptorProvider));
      }

      if (o instanceof PEMKeyPair) {
        // eg, "openssl genrsa -out keypair-rsa-2048-unencrypted.pem 2048"
        return converter.getKeyPair((PEMKeyPair) o);
      }
    }
    throw new KeyParseException("unknown object type when decoding private key");
  }

  private static String reformIndents(String s) {
    return s.trim().replaceAll("([\\r|\\n] +)", "\n");
  }

  public static PublicKey decodePublicKey(String publicKeyString) throws KeyParseException {
    try {
      JcaPEMKeyConverter converter =
          new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
      publicKeyString = reformIndents(publicKeyString);
      PEMParser pemParser = new PEMParser(new StringReader(publicKeyString));
      Object object = pemParser.readObject();
      if (object == null) {
        throw new KeyParseException("unable to read anything when decoding public key");
      }
      return converter.getPublicKey((SubjectPublicKeyInfo) object);
    } catch (KeyParseException exc0) {
      throw exc0;
    } catch (Exception exc1) {
      throw new KeyParseException("cannot instantiate public key", exc1);
    }
  }

  public static X509Certificate decodeCertificate(String certificateString)
      throws KeyParseException {
    try {
      CertificateFactory certFactory =
          CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
      certificateString = reformIndents(certificateString);
      return (X509Certificate)
          (certFactory.generateCertificate(
              new ByteArrayInputStream(certificateString.getBytes(StandardCharsets.UTF_8))));
    } catch (Exception ex) {
      throw new KeyParseException("cannot instantiate public key from certificate", ex);
    }
  }

  public static String toPem(final X509Certificate certificate)
      throws CertificateEncodingException {
    final Base64.Encoder encoder = Base64.getMimeEncoder(64, LINE_SEPARATOR.getBytes());
    final String encodedCertText = new String(encoder.encode(certificate.getEncoded()));
    return BEGIN_CERT + LINE_SEPARATOR + encodedCertText + LINE_SEPARATOR + END_CERT;
  }

  public static byte[] fingerprintBytes(final X509Certificate certificate)
      throws NoSuchAlgorithmException, CertificateEncodingException, NoSuchProviderException {
    byte[] encodedCert = certificate.getEncoded();
    MessageDigest md = MessageDigest.getInstance("SHA-256", BouncyCastleProvider.PROVIDER_NAME);
    return md.digest(encodedCert);
  }

  // public static String fingerprintHex(X509Certificate certificate)
  //     throws CertificateEncodingException, NoSuchAlgorithmException, NoSuchProviderException {
  //   return Hex.toHexString(fingerprintBytes(certificate)).toUpperCase();
  // }

  public static String fingerprintBase64(X509Certificate certificate)
      throws CertificateEncodingException, NoSuchAlgorithmException, NoSuchProviderException {
    return Base64.getEncoder().withoutPadding().encodeToString(fingerprintBytes(certificate));
  }
}
