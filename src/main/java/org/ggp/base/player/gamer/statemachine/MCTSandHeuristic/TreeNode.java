package org.ggp.base.player.gamer.statemachine.MCTSandHeuristic;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TreeNode {
    //uct的那个参数c 默认 根号2 1.414
    static public Double c = Math.sqrt(2);
    //true max, false min
    private Boolean isMax;
    private Move move;

    //经过该点的次数
    private Integer n_k;
    //经过该点获胜的次数
    private Double w_k;

    //孩子节点list
    private List<TreeNode> child;
    //可有的孩子节点个数
    private Integer legalMoveNum;

    //根结点用的构造函数
    public TreeNode(Integer legalMoveNum){
        this.n_k = 0;
        this.w_k = 0.0;
        this.child = new ArrayList<>();
        this.legalMoveNum = legalMoveNum;
        this.isMax = true;
    }
    //用的构造函数
    public TreeNode(Move move, Integer legalMoveNum, Boolean isMax){
        this.move = move;
        this.n_k = 0;
        this.w_k = 0.0;
        this.child = new ArrayList<>();
        this.legalMoveNum = legalMoveNum;
        this.isMax = isMax;
    }

    //获取UCT值
    public Double getUCT(Integer n_p){
        return w_k /n_k+c*Math.sqrt(2 * Math.log(n_p)/n_k);
    }
    //获取该节点最合适的子节点
    public TreeNode checkBestChild(){
        ArrayList<Double> list = new ArrayList<>();
        for (TreeNode i:
                child) {
            list.add(i.getUCT(n_k));
        }

        int index = 0;
        if (this.isMax){
            index = list.indexOf(Collections.max(list));
        }
        else{
            index = list.indexOf(Collections.min(list));
        }
        return child.get(index);
    }

    public List<TreeNode> getChild() {
        return child;
    }

    public Move getMove() {
        return move;
    }

    public void setMove(Move move) {
        this.move = move;
    }

    public Integer getN_k() {
        return n_k;
    }

    public void setN_k(Integer n_k) {
        this.n_k = n_k;
    }

    public Double getW_k() {
        return w_k;
    }

    public void setW_k(Double w_k) {
        this.w_k = w_k;
    }

    public Integer getLegalMoveNum() {
        return legalMoveNum;
    }

    public Boolean getMax() {
        return isMax;
    }

    public void setChild(List<TreeNode> child) {
        this.child = child;
    }

    public void setLegalMoveNum(Integer legalMoveNum) {
        this.legalMoveNum = legalMoveNum;
    }
}