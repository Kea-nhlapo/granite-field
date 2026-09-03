ALTER TABLE access_phone_identity
    DROP CONSTRAINT access_phone_identity_method;

UPDATE access_phone_identity
SET verification_method = 'OTP'
WHERE verification_method = 'TWILIO_OTP';

ALTER TABLE access_phone_identity
    ADD CONSTRAINT access_phone_identity_method CHECK (
        verification_method IN ('OTP', 'MOMO_CONSENT')
    );
