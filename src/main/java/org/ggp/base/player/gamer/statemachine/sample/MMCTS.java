package org.ggp.base.player.gamer.statemachine.sample;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class MMCTS extends SampleGamer {
	//MCTS mcts;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		StateMachine theMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> moves = theMachine.getLegalMoves(getCurrentState(), getRole());
        Move selection = moves.get(0);
        MCTS mcts = new MCTS(finishBy,theMachine,getRole());
        selection = mcts.search(getCurrentState());

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
		//StateMachine theMachine = getStateMachine();
		//long finishBy = timeout - 1000;
		//mcts = new MCTS(finishBy,theMachine,getRole());
		//mcts.search(getCurrentState());
    }
}
class Action{
	Role role;
	Move move;
	Action(Role role, Move move){
		this.role=role;
		this.move=move;
	}
	@Override
	public boolean equals(Object obj) {
		if(this==obj) return true;
		if(!(obj instanceof Action)) return false;
		Action objAction=(Action)obj;
		return this.role.equals(objAction.role)&&this.move.equals(objAction.move);
	}
	@Override
	public int hashCode() {
		int result = 17;
        result = 31 * result + (role == null ? 0 : role.hashCode());
        result = 31 * result + (move == null ? 0 : move.hashCode());
        return result;
	}
}

class treeNode{
	StateMachine theMachine;
	MachineState state;
	Role role; //每个节点都是一个状态，每个节点的role都是getRole()
	boolean isTerminal;
	boolean isFullyExpanded;
	treeNode parent;
	int numVisits;
	int totalReward;
	Map<Move,treeNode> children;
	List<Role> roles;
	treeNode(MachineState state, treeNode parent, StateMachine theMachine, Role role){
		this.state = state;
		this.theMachine = theMachine;
	    this.isTerminal = theMachine.isTerminal(this.state);
	    this.isFullyExpanded = this.isTerminal;
	    this.parent = parent;
	    this.numVisits = 0;
	    this.totalReward = 0;
	    this.children = new HashMap<Move,treeNode>();
	    this.roles=theMachine.getRoles();
	    this.role = role;
	    //this.nextrole = getNextRole(this.role);

	}
	/*private Role getNextRole(Role role) {
		// TODO Auto-generated method stub
		for(int i=0;i<roles.size();i++) {
			if(roles.get(i).equals(role)) {
				if(i+1<roles.size()) {
					this.nextrole=roles.get(i+1);
				}
				else this.nextrole=roles.get(0);
			}
		}
		return null;
	}*/
	public List<Move> getPossibleActions() {
		// TODO Auto-generated method stub
		try {
			List<Move> actions=theMachine.getLegalMoves(state, role);
			return actions;
		} catch (MoveDefinitionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		};
		return null;
	}

}

class Memory{
	int length;
	float update_rate;
	List<MachineState> keys;
	Map<MachineState,Integer> items;

	Memory(){
		int length = 100;
		float update_rate = (float) 0.2;
		this.length=length;
		this.update_rate=update_rate;
		this.keys=new ArrayList<MachineState>();
		this.items=new HashMap<>();
	}

	boolean update(MachineState graph, int value) {
		if(keys.isEmpty()) {
			keys.add(graph);
	        items.put(graph, value);
	        return false;
		}
		for(int i=0;i<keys.size();i++) {
			if(keys.get(i).equals(graph)) {
				keys.add(graph);
				keys.remove(i);
				items.put(graph, (int) (update_rate * value + (1-update_rate) * items.get(graph)));
				return true;
			}
		}
		if(keys.size()==length){
			items.remove(keys.get(0));
			keys.remove(0);
		}
        keys.add(graph);
        items.put(graph, value);
        return false;
	}

	int query(MachineState graph){
		if(!keys.isEmpty()) {
			for(int i=0;i<keys.size();i++) {
				if(keys.get(i).equals(graph)) {
					keys.add(graph);
					keys.remove(i);
					return items.get(graph);
				}
			}
		}
		return 0;
	}
}

class MCTS{
	long timelimit;
	double explorationConstant;
	Memory memoryCore;
	StateMachine theMachine;
	Role role;
	treeNode root;
	int searchtime;
	MCTS(long timelimit, StateMachine theMachine, Role role){
		this.timelimit=timelimit;
		this.explorationConstant=1/Math.sqrt(2);
		this.theMachine=theMachine;
		this.memoryCore=new Memory();
		this.role=role;
		this.searchtime=0;
	}

	public int randomPolicy(MachineState state) {
		try {
			while(!theMachine.isTerminal(state)) {
				state = theMachine.getNextStateDestructively(state, theMachine.getRandomJointMove(state));
			}
			return theMachine.getGoal(state, role);
		}catch(Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	public Move search(MachineState initialState) {
		this.root=new treeNode(initialState,null,theMachine,role);
		while(System.currentTimeMillis()<timelimit) {
			executeRound();
			searchtime++;
		}
		treeNode bestChild=getBestChild(root, 0);
		System.out.println("MMCTS's search time is: "+searchtime);
		return getAction(root,bestChild);

	}

	private Move getAction(treeNode root, treeNode bestChild) {
		// TODO Auto-generated method stub
		Set<Move> keySet=root.children.keySet();
		Iterator<Move> it=keySet.iterator();
		while(it.hasNext()) {
			Move key=it.next();
			if(root.children.get(key).equals(bestChild)) {
				return key;
			}
		}
		return null;
	}

	private treeNode getBestChild(treeNode node, double explorationValue) {
		// TODO Auto-generated method stub
		double bestValue=Double.NEGATIVE_INFINITY;
		List<treeNode> bestNodes = new ArrayList<treeNode>();
		Set<Move> keySet=node.children.keySet();
		Iterator<Move> it=keySet.iterator();

		while(it.hasNext()) {
			Move key=it.next();
			treeNode child=node.children.get(key);
			double nodeValue=child.totalReward / child.numVisits + explorationValue * Math.sqrt(
	                2 * Math.log(node.numVisits) / child.numVisits);
			if(nodeValue>bestValue) {
				bestValue=nodeValue;
				bestNodes.clear();
				bestNodes.add(child);
			}
			else if(nodeValue==bestValue) {
				bestNodes.add(child);
			}

		}
		Random random=new Random();
		int r=random.nextInt(bestNodes.size());
		return bestNodes.get(r);
	}

	public void executeRound() {
		// TODO Auto-generated method stub
		treeNode node=selectNode(this.root);
		int reward=rollout(node.state);
		this.memoryCore.update(node.state, reward);
		reward=this.memoryCore.query(node.state);
		backpropogate(node,reward);
	}

	private void backpropogate(treeNode node, int reward) {
		// TODO Auto-generated method stub
		while(node!=null) {
			node.numVisits += 1;
			node.totalReward += reward;
			node = node.parent;
	    }
	}

	private int rollout(MachineState state) {
		// TODO Auto-generated method stub
		return randomPolicy(state);
	}

	private treeNode selectNode(treeNode node) {
		// TODO Auto-generated method stub
		while(!node.isTerminal) {
			if(node.isFullyExpanded) {
				node=getBestChild(node, this.explorationConstant);
			}
			else {
				return expand(node);
			}
		}
		return node;
	}

	private treeNode expand(treeNode node) {
		// TODO Auto-generated method stub
		List<Move> actions = node.getPossibleActions();
		for(int i=0;i<actions.size();i++) {
			if(!node.children.keySet().contains(actions.get(i))) {
				treeNode newNode = null;
				try {
					newNode = new treeNode(theMachine.getRandomNextState(node.state, this.role, actions.get(i)),node,theMachine,this.role);
				} catch (MoveDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (TransitionDefinitionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				node.children.put(actions.get(i), newNode);
				if(actions.size()==node.children.size()) {
					node.isFullyExpanded=true;
				}
				return newNode;
			}
		}
		return null;
	}
}