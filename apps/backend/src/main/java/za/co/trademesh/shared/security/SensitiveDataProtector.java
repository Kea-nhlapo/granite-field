package za.co.trademesh.shared.security;

/** Encrypts small pieces of sensitive application data before persistence. */
public interface SensitiveDataProtector {

    String protect(String plainText);

    String unprotect(String protectedText);
}
