package za.co.trademesh.modules.notification.application;

public interface NotificationDataProtector {

    String protect(String plainText);

    String unprotect(String protectedText);
}
