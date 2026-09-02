package za.co.trademesh.modules.access.application;

public interface OtpProvider {

    void send(String phoneNumber);

    boolean verify(String phoneNumber, String code);
}
