package org.ggp.base.player.gamer.statemachine.testplayer;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

public class MonteCarloTreeSearchThread implements Runnable {
    private List<Object> channel = null;
    private TreeNode Root = null;
    private StateMachine theMachine = null;
    private Role me = null;
    private Role opponent = null;
    private MachineState theState = null;
    private List<Move> moveList = null;
    private int[] depth = new int[1];

    public MonteCarloTreeSearchThread(List<Object> channel, TreeNode root, StateMachine theMachine, Role me, Role opponent, MachineState theState, List<Move> moveList) {
        this.channel = channel;
        Root = root;
        this.theMachine = theMachine;
        this.me = me;
        this.opponent = opponent;
        this.theState = theState;
        this.moveList = moveList;
    }

    @Override
    public void run() {
        try {

            //----------蒙特卡洛树搜索------------------
            while (theMachine.isTerminal(theState)) {
                int score;
                List<Move> moves = moveList;
                //tmp节点一开始指向根结点
                TreeNode tmp = Root;
                //获取游戏当前局面状态
                MachineState state = theState;

                //创建反向传播列表 用于最后反向传播是更新对应的w_k值
                List<TreeNode> backList = new ArrayList<>();

                //加入的节点n_k值加一
                tmp.setN_k(tmp.getN_k() + 1);
                backList.add(tmp);

                //只要满足选举的条件——该节点的孩子数等于他可有的合法孩子节点数 即所有孩子都至少走过一次
                while (tmp.getChild().size() == tmp.getLegalMoveNum()) {
                    //select
                    //选举一个最合适的孩子节点
                    tmp = tmp.checkBestChild();

                    //把该节点加进去 n_k值加一
                    tmp.setN_k(tmp.getN_k() + 1);
                    backList.add(tmp);

                    //状态转换
//                    if (tmp.getMax()) {
//                        state = theMachine.getNextState(state, theMachine.getRandomJointMove(state, me, tmp.getMove()));
//                    }
//                    else {
//                        state = theMachine.getNextState(state, theMachine.getRandomJointMove(state, opponent, tmp.getMove()));
//                    }
                    state = tmp.getTheState();
                    if (theMachine.isTerminal(state))
                        break;
                }
                //判断是否为终态
                if (!theMachine.isTerminal(state)) {
                    //expand
                    //扩展 判断是哪种节点要扩展
                    TreeNode expandNode = null;
                    Boolean thisMax = !tmp.getMax();

                    boolean hasNode = false;

                    //从当前状态获取自己的所有合法动作
                    if (tmp != Root) {
                        if (thisMax) {
                            moves = theMachine.getLegalMoves(state, me);
                        } else {
                            moves = theMachine.getLegalMoves(state, opponent);
                        }
                    }

                    //随机选一个
                    Move moveUnderConsideration = moves.get(ThreadLocalRandom.current().nextInt(moves.size()));

                    //判断这一个点是不是已经被创建了 从tmp的child里找
                    for (TreeNode i :
                            tmp.getChild()) {
                        //如果找到一样的就直接模拟
                        if (i.getMove().equals(moveUnderConsideration)) {
                            expandNode = i;
                            hasNode = true;
                            break;
                        }
                    }

                    if (!hasNode) {
                        //创建扩展节点
                        expandNode = new TreeNode(moveUnderConsideration, moves.size(), thisMax, null);
                        //把子节点加进父节点child中
                        tmp.getChild().add(expandNode);
                    }

                    if (expandNode.getMax()) {
                        state = theMachine.getNextState(state, theMachine.getRandomJointMove(state, me, moveUnderConsideration));
                    } else {
                        state = theMachine.getNextState(state, theMachine.getRandomJointMove(state, opponent, moveUnderConsideration));
                    }
                    expandNode.setTheState(state);


                    //把孩子节点加进反向传播列表中
                    backList.add(expandNode);
                    expandNode.setN_k(expandNode.getN_k() + 1);

                    //simulation
                    state = theMachine.performDepthCharge(state, depth);
                }

                score = theMachine.getGoal(state, me);
                //back 反向传播回溯结果
                for (TreeNode i :
                        backList) {
                    i.setW_k(i.getW_k() + score / 100.0);
                }

                synchronized (channel) {
                    try {
                        channel.wait();
                    }
                    catch (Exception e) {
                        System.out.println("现在是thread里e");
                        e.printStackTrace();
                    }
                    System.out.println("现在是thread里");
                    Integer code = (Integer)channel.get(0);
                    if (code == 1) {
                        theState = (MachineState) channel.get(1);
                        moveList = (List<Move>) channel.get(2);
                        TreeNode tmp_root_node = null;
                        for (TreeNode child:
                             Root.getChild()) {
                            if (child.getTheState().equals(theState)) {
                                tmp_root_node = child;
                                List<TreeNode> child_tmp = new ArrayList<>();

                                for (TreeNode c:
                                        tmp_root_node.getChild()) {
                                    if (moveList.contains(c.getMove())) {
                                        child_tmp.add(c);
                                    }
                                }
                                tmp_root_node.setChild(child_tmp);
                                tmp_root_node.setLegalMoveNum(moveList.size());
                                break;
                            }
                        }

                        if(tmp_root_node == null) {
                            tmp_root_node = new TreeNode(moveList.size(), !Root.getMax(), theState);
                        }
                        Root = tmp_root_node;

                        channel.set(0, 0);
                    }
                    else if (code == 2) {
                        Root = Root.checkBestChild();
                        channel.set(3, Root.getMove());
                        channel.set(0, 0);
                    }
                }
                channel.notifyAll();
            }
        }
        catch (MoveDefinitionException | TransitionDefinitionException | GoalDefinitionException e) {
            System.out.println(123);
            e.printStackTrace();
        }
    }
}
