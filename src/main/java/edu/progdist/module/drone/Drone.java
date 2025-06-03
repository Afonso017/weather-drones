package edu.progdist.module.drone;

public class Drone {
    EnviromentData data;
    String separator;
    String prefix;
    String sufix;

    public Drone(String separator, String prefix, String suffix) {
        this.data = new EnviromentData();
        this.separator = separator;
        this.prefix = prefix;
        this.sufix = suffix;
    }

    public Drone(int pressao, double radiacao, int temperatura, int umidade, String separator, String prefix, String sufix) {
        this.data = new EnviromentData(pressao, radiacao, temperatura, umidade);
        this.separator = separator;
        this.prefix = prefix;
        this.sufix = sufix;
    }

    public Drone(EnviromentData data, String separator, String prefix, String sufix) {
        this.data = new EnviromentData(data);
        this.separator = separator;
        this.prefix = prefix;
        this.sufix = sufix;
    }

    public EnviromentData getData() {
        return data;
    }

    public void setData(EnviromentData data) {
        this.data = data;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public void randomize() {
        data.randomize();
    }

    @Override
    public String toString() {
        if (prefix == null) {
            return data.getPressao() + separator +
                   data.getRadiacao() + separator +
                   data.getTemperatura() + separator +
                   data.getUmidade();
        } else {
            return prefix + data.getPressao() + separator +
                   data.getRadiacao() + separator +
                   data.getTemperatura() + separator +
                   data.getUmidade() + sufix;
        }
    }
}
