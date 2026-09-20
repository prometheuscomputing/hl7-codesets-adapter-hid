package gov.nist.hit.hl7.codeset.adapter.model.response;

import gov.nist.hit.hl7.codeset.adapter.model.Code;

public class CodeResponse {
    private String value;
    private String codeSystem;
    private String displayText;

    private boolean isPattern;
    private String regularExpression;
    private String usage;
    public CodeResponse(){

    }
    public CodeResponse(Code code){
        this.value = code.getValue();
        this.codeSystem = code.getCodeSystem();
        this.displayText = code.getDescription();
//        this.isPattern = code.pa;
        this.regularExpression = code.getRegexRule();
        this.usage = code.getUsage();
    }
    public CodeResponse(String value, String codeSystem, String displayText, boolean isPattern, String regularExpression, String usage) {
        this.value = value;
        this.codeSystem = codeSystem;
        this.displayText = displayText;
        this.isPattern = isPattern;
        this.regularExpression = regularExpression;
        this.usage = usage;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getCodeSystem() {
        return codeSystem;
    }

    public void setCodeSystem(String codeSystem) {
        this.codeSystem = codeSystem;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    public boolean isPattern() {
        return isPattern;
    }

    public void setPattern(boolean pattern) {
        isPattern = pattern;
    }

    public String getRegularExpression() {
        return regularExpression;
    }

    public void setRegularExpression(String regularExpression) {
        this.regularExpression = regularExpression;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }



}
