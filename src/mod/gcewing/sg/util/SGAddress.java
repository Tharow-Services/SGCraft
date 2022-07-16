package gcewing.sg.util;

public class SGAddress {
    private int d, cx, cz;

    public SGAddress(int d, int cx, int cz) {this.d=d;this.cx=cx;this.cz=cz;}
    public SGAddress(String s) {
        String[] b=s.split(" ");
        this.d = Integer.parseInt(b[0]);
        this.cx = Integer.parseInt(b[1]);
        this.cz = Integer.parseInt(b[2]);
    }

    public int getD() {return this.d;}
    public int getCX() {return this.cx;}
    public int getCZ() {return this.cz;}

    @Override
    public String toString() {
        return this.d+" "+this.cx+" "+this.cz;
    }
}