package main;

import java.util.*;

public class Stats {

    public int bonus = 0;

    public int coffee = 0;
    public int tea = 0;

    public int coinWins;
    public int coinLosses;
    public int trees;
    public int capitals;
    public Stats(){

    }

    public Stats(int bonus, int coinWins, int coinLosses, int coffee, int tea, int trees, int capitals){
        this.coinWins = coinWins;
        this.coinLosses = coinLosses;
        this.coffee = coffee;
        this.tea = tea;
        this.bonus = bonus;
        this.trees = trees;
        this.capitals = capitals;
    }




}
