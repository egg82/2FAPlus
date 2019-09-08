package me.egg82.tfaplus.auth.data;

import java.util.UUID;

public class AuthyData extends AuthenticationData {
    private final long id;
    private final String email;
    private final String phone;
    private final String countryCode;

    public AuthyData(UUID uuid, long id) {
        super(uuid);
        this.id = id;
        this.email = null;
        this.phone = null;
        this.countryCode = null;
    }

    public AuthyData(UUID uuid, String email, String phone, String countryCode) {
        super(uuid);

        if (email == null) {
            throw new IllegalArgumentException("email cannot be null.");
        }
        email = email.trim();
        if (email.isEmpty()) {
            throw new IllegalArgumentException("email cannot be empty.");
        }

        if (phone == null) {
            throw new IllegalArgumentException("phone cannot be null.");
        }
        phone = phone.trim();
        if (phone.isEmpty()) {
            throw new IllegalArgumentException("phone cannot be empty.");
        }

        if (countryCode == null) {
            throw new IllegalArgumentException("countryCode cannot be null.");
        }
        countryCode = countryCode.trim();
        if (countryCode.isEmpty()) {
            throw new IllegalArgumentException("countryCode cannot be empty.");
        }

        this.id = -1L;
        this.email = email;
        this.phone = phone;
        this.countryCode = countryCode;
    }

    public long getID() { return id; }

    public String getEmail() { return email; }

    public String getPhone() { return phone; }

    public String getCountryCode() { return countryCode; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthyData)) return false;
        AuthyData authyData = (AuthyData) o;
        return uuid.equals(authyData.uuid);
    }
}
