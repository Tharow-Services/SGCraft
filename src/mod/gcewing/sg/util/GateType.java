//------------------------------------------------------------------------------------------------
//
//   SG Craft - Visual and sound style of a stargate
//
//------------------------------------------------------------------------------------------------

package gcewing.sg.util;

public enum GateType {

    MILKY_WAY(1, "milkyway"),
    PEGASUS(2, "pegasus"),
    // The Destiny gate of Stargate Universe
    UNIVERSE(3, "universe");

    // Value stored in SGBaseTE.gateType and in NBT
    public final int id;
    // Name used in texture paths
    public final String assetName;

    GateType(int id, String assetName) {
        this.id = id;
        this.assetName = assetName;
    }

    // Anything other than Pegasus or Universe is treated as Milky Way
    public static GateType fromId(int id) {
        for (GateType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return MILKY_WAY;
    }

    // Universe gates share the Milky Way gate's sounds and DHD, so they take its choice
    public <T> T select(T milkyWay, T pegasus) {
        return this == PEGASUS ? pegasus : milkyWay;
    }

    public <T> T select(T milkyWay, T pegasus, T universe) {
        return this == PEGASUS ? pegasus : this == UNIVERSE ? universe : milkyWay;
    }

}
