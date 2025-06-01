package edu.progdist.module.drone;

public class EnviromentData {
    // pressão em hPA (hectopascal)
    // Medida mundial entre 870 e 1083
    int pressao;

    // radiação em kW/m² (kilowatt por metro quadrado)
    // Media brasileira entre 4,5 e 6,5 por dia
    double radiacao;

    //temperatura medida em ªC (graus célsius)
    // Media mundial entre -89 e 56
    int  temperatura;

    //umidade medida em % da capacidade de retenção do ar
    // Media mundial entre 15 e 70
    int umidade;


    public EnviromentData() {}

    public EnviromentData(int pressao, double radiacao, int temperatura, int umidade) {
        this.pressao = pressao;
        this.radiacao = radiacao;
        this.temperatura = temperatura;
        this.umidade = umidade;
    }

    public EnviromentData(EnviromentData enviromentData) {
        this.pressao = enviromentData.pressao;
        this.radiacao = enviromentData.radiacao;
        this.temperatura = enviromentData.temperatura;
        this.umidade = enviromentData.umidade;
    }

    public void randomize() {
        java.util.Random rand = new java.util.Random();
        // pressão: 870 to 1083
        this.pressao = 870 + rand.nextInt(1082 - 870 + 1);
        // radiação: 4.5 to 6.5 (rounded to 2 decimal places)
        this.radiacao = Math.round((4.5 + (6.5 - 4.5) * rand.nextDouble()) * 100.0) / 100.0;
        // temperatura: -89 to 56
        this.temperatura = -89 + rand.nextInt(56 - (-89) + 1);
        // umidade: 15 to 70
        this.umidade = 15 + rand.nextInt(70 - 15 + 1);
    }

    public int getPressao() {
        return pressao;
    }

    public void setPressao(int pressao) {
        this.pressao = pressao;
    }

    public double getRadiacao() {
        return radiacao;
    }

    public void setRadiacao(double radiacao) {
        this.radiacao = radiacao;
    }

    public int getTemperatura() {
        return temperatura;
    }

    public void setTemperatura(int temperatura) {
        this.temperatura = temperatura;
    }

    public int getUmidade() {
        return umidade;
    }

    public void setUmidade(int umidade) {
        this.umidade = umidade;
    }
}
