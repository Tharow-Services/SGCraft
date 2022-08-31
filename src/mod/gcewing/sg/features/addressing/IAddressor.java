package gcewing.sg.features.addressing;

public interface IAddressor {
    String getSymbolChars();
    int getNumSymbols();
    void validateAddress(String address) throws AddressingError;
    String validateNormalizeAddress();
    boolean inSameZone(String origin, String destination);
}
