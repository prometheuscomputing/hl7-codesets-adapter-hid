package gov.nist.hit.hl7.codeset.adapter.model;

public class Provider {

    private String name;
    private String label;

    public Provider() {
    }

    public Provider(String name, String label) {
        this.name = name;
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
