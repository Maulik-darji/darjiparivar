package com.example.darjiparivar.data;

public class Profile {
    public final String id;
    public final boolean female;
    public final int age;
    public final String height;
    public final boolean verified;
    public final boolean premium;
    public final boolean online;
    public final int color;

    /** Human-friendly member id shown in the UI, e.g. "DP10231". */
    public String displayId;
    /** Main photo, or null to fall back to the initials avatar. */
    public String photoUrl;
    /** All photos the viewer may see, main first. Filled in by {@code Repository.profile()}. */
    public java.util.List<String> photoUrls = new java.util.ArrayList<>();

    private final Text name, city, state, community, education, profession, income,
            family, about;

    public Profile(String id, Text name, boolean female, int age, String height, Text city,
                   Text state, Text community, Text education, Text profession,
                   Text income, Text family, Text about,
                   boolean verified, boolean premium, boolean online, int color) {
        this.id = id;
        this.name = name;
        this.female = female;
        this.age = age;
        this.height = height;
        this.city = city;
        this.state = state;
        this.community = community;
        this.education = education;
        this.profession = profession;
        this.income = income;
        this.family = family;
        this.about = about;
        this.verified = verified;
        this.premium = premium;
        this.online = online;
        this.color = color;
        this.displayId = id;
    }

    public String name() { return name.get(); }
    public String city() { return city.get(); }
    public String state() { return state.get(); }
    public String community() { return community.get(); }
    public String education() { return education.get(); }
    public String profession() { return profession.get(); }
    public String income() { return income.get(); }
    public String family() { return family.get(); }
    public String about() { return about.get(); }

    public String firstName() {
        String n = name();
        int space = n.indexOf(' ');
        return space > 0 ? n.substring(0, space) : n;
    }

    public String initials() {
        String n = name().trim();
        if (n.charAt(0) > 0x7F) return firstSyllable(n);
        String[] parts = n.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.toString();
    }

    /**
     * First akshara of an Indic name, e.g. "રિ" for "રિયા". Two-letter initials read badly in
     * Gujarati (ર and દ look like the digits ૨ and ૬), so a single syllable is used instead.
     */
    private static String firstSyllable(String s) {
        int end = 1;
        while (end < s.length()) {
            int type = Character.getType(s.charAt(end));
            boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
            boolean afterVirama = s.charAt(end - 1) == '્' && Character.isLetter(s.charAt(end));
            if (!mark && !afterVirama) break;
            end++;
        }
        return s.substring(0, end);
    }

    public String location() {
        return city() + ", " + state();
    }

    public boolean abroad() {
        return state.en.equals("United Kingdom") || state.en.equals("Canada");
    }
}
