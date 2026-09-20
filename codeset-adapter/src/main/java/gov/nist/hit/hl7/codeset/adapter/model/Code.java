package gov.nist.hit.hl7.codeset.adapter.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.UUID;
@Document
public class Code  {
    /**
     *
     */
    @Id
    private String id;
    public String codesetversionId;  // Reference to the CodesetVersion

    private String value;
    private String description;
    private String codeSystem;
    private String comments;
    private String display;
    private String usage;
    private String definition;
    private String v2TableStatus;
    private String deprecated;
    private String v2ConceptComment;
    private String v2ConceptCommentAsPublished;


    private String codeType;
    private String regexRule;
    private boolean exclude;

    public Code() {
        this.setId(new ObjectId().toString());
    }

    public Code(String value, String description, String codeSystem, String comments) {
        this.setId(new ObjectId().toString());
        this.value = value;
        this.description = description;
        this.codeSystem = codeSystem;
        this.comments = comments;
    }

    public String getCodesetversionId() {
        return codesetversionId;
    }

    public void setCodesetversionId(String codesetversionId) {
        this.codesetversionId = codesetversionId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCodeSystem() {
        return codeSystem;
    }

    public void setCodeSystem(String codeSystem) {
        this.codeSystem = codeSystem;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public String getV2TableStatus() {
        return v2TableStatus;
    }

    public void setV2TableStatus(String v2TableStatus) {
        this.v2TableStatus = v2TableStatus;
    }

    public String getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(String deprecated) {
        this.deprecated = deprecated;
    }

    public String getV2ConceptComment() {
        return v2ConceptComment;
    }

    public void setV2ConceptComment(String v2ConceptComment) {
        this.v2ConceptComment = v2ConceptComment;
    }

    public String getV2ConceptCommentAsPublished() {
        return v2ConceptCommentAsPublished;
    }

    public void setV2ConceptCommentAsPublished(String v2ConceptCommentAsPublished) {
        this.v2ConceptCommentAsPublished = v2ConceptCommentAsPublished;
    }

    public String getCodeType() {
        return codeType;
    }

    public void setCodeType(String codeType) {
        this.codeType = codeType;
    }

    public String getRegexRule() {
        return regexRule;
    }

    public void setRegexRule(String regexRule) {
        this.regexRule = regexRule;
    }

    public boolean isExclude() {
        return exclude;
    }

    public void setExclude(boolean exclude) {
        this.exclude = exclude;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((codeSystem == null) ? 0 : codeSystem.hashCode());
        result = prime * result + ((value == null) ? 0 : value.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Code other = (Code) obj;
        if (codeSystem == null) {
            if (other.codeSystem != null)
                return false;
        } else if (!codeSystem.equals(other.codeSystem))
            return false;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

}
