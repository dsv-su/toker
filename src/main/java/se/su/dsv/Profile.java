package se.su.dsv;

import java.util.Objects;

public final class Profile {
    private final String principal;

    public Profile(final String principal) {
        this.principal = principal;
    }

    public String getPrincipal() {
        return principal;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof Profile)) return false;
        final Profile profile = (Profile) o;
        return Objects.equals(principal, profile.principal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(principal);
    }
}
