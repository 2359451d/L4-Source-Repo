package type;

import java.util.Set;

public class SignatureSet extends Type{

    private Set<Signature> acceptableSignatures;
    private Set<String> acceptableTypes;
    private Set<String> typeOrderToBeChecked;

    public SignatureSet(Set<Signature> acceptableSignatures, Set<String> acceptableTypes) {
        this.acceptableSignatures = acceptableSignatures;
        this.acceptableTypes = acceptableTypes;
    }

    public SignatureSet(Set<Signature> acceptableSignatures, Set<String> acceptableTypes, Set<String> typeOrderToBeChecked) {
        this.acceptableSignatures = acceptableSignatures;
        this.acceptableTypes = acceptableTypes;
        this.typeOrderToBeChecked = typeOrderToBeChecked;
    }

    public Set<Signature> getAcceptableSignatures() {
        return acceptableSignatures;
    }

    public Set<String> getAcceptableTypes() { return acceptableTypes; }

    public Set<String> getTypeOrderToBeChecked() { return typeOrderToBeChecked; }

    @Override
    public String toString() {
        return "SignatureSet{" +
                "acceptableSignatures=" + acceptableSignatures +
                "\n, acceptableTypes=" + acceptableTypes +
                '}';
    }

    @Override
    public boolean equiv(Type type) {
        return false;
    }
}