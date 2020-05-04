/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 
Modified by Yuri Ardila, 2015

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
*****************************************************************/

package blindAuction;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.*;

/**
 * JADE agent representing a bidder of an auction.
 * It has single sequential behavior representing its lifecycle.
 * It will terminate after the budget runs out.
 */
public class BidderHuman extends Agent {

	// The catalogue of books for sale (maps the title of a book to its price)
	public String itemName;

	// The GUI by means of which the user can add items in the catalogue
	private BidderHumanGUI myGui;

    // The auctioneer
    private AID AuctioneerAID;

    private ACLMessage msgCFP;

    // The budget left for this bidder
    public int budget;

    // Random number generator
    static Random rn = new Random();

	// Put agent initializations here
	protected void setup() {

        // Setup budget randomly between 1000 - 2000
        budget = rn.nextInt(1000) + 1000;
		System.out.println("Hello! Bidder "+getAID().getName()+" is ready with budget " + budget);

		// Create and show the GUI 
		myGui = new BidderHumanGUI(this);
		myGui.showGui();

		// Register as bidder to the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("blind-auction");
		sd.setName("Blind-Auction");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Add the behaviour for receiving CFP from Auctioneer
		addBehaviour(new ReceiveCFPHuman(this));

		// Add the behaviour for receiving item --as the auction winner
		addBehaviour(new ReceiveItemAsWinnerHuman(this));
	}

	// RefreshGUI
	protected void refreshGUI() {
        myGui.dispose();
        myGui = new BidderHumanGUI(this);
        myGui.showGui();
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		}
		catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Close the GUI
		myGui.dispose();

		// Printout a dismissal message
		System.out.println("Bidder "+getAID().getName()+" terminating.");
	}

    // Set the current auctioneer
    public void setAuctioneer(AID agentAID) {
        this.AuctioneerAID = agentAID;
    }    

    public void setMsgCFP(ACLMessage msg) {
        this.msgCFP = msg;
    }

    // Add money to current wallet
    public int getMoney() {
        return this.budget;
    }    

    // Add money to current wallet
    public void addMoney(final int newMoney) {
		addBehaviour(
                     new OneShotBehaviour() {
                         public void action() {
                             budget += newMoney;
                             System.out.println(newMoney + " is added to wallet");
                         }
                     }
                     );        
        refreshGUI();
    }    

    public void sendBid(final int bidPrice) {
		addBehaviour(
                     new OneShotBehaviour() {
                         public void action() {

                             ACLMessage bid = msgCFP.createReply();
                             bid.addReceiver(AuctioneerAID);

                             bid.setPerformative(ACLMessage.PROPOSE);
                             
                             // Check if budget is adequate 
                             if (budget >= bidPrice) {
                                 bid.setContent(String.valueOf(bidPrice));
                                 System.out.println(getLocalName() + " sent bid with price " + bidPrice);
                                 send(bid);
                             }
                             else {
                                 System.out.println("Budget not enough");
                                 System.out.println("Current budget: " + budget + ", Your bid : " + bidPrice);
                             }	
                         }
                     }
                     );        
    }

    public void refuseBid() {
		addBehaviour(
                     new OneShotBehaviour() {
                         public void action() {
                             ACLMessage refuse = msgCFP.createReply();
                             refuse.setPerformative(ACLMessage.REFUSE);
                             refuse.setContent("Not joining");
                             send(refuse);
                             System.out.println("Not joining this time");
                         }
                     }
                     );        
    }

}

/**
 * Process CFP as a computer, whether to bid on that item or not
 * All-in strategy. Bid with all the money it has.
 */
class ReceiveCFPHuman extends CyclicBehaviour {

    private BidderHuman myAgent;

    public ReceiveCFPHuman(BidderHuman agent) {
        super(agent);
        myAgent = agent;
    }

    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
        ACLMessage msg = myAgent.receive(mt);
        if (msg != null) {
            // CFP Message received. Process it
            String ans = msg.getContent();
            String[] parts = ans.split(",");
            String itemName = parts[0];
            int itemInitialPrice = Integer.parseInt(parts[1]);
            int bidPrice = myAgent.budget;

            myAgent.setAuctioneer(msg.getSender());
            myAgent.setMsgCFP(msg);

            System.out.println("Auction commenced. Current item is " + itemName);
            System.out.println("Current item initial price is " + itemInitialPrice);
        }
        else {
            block();
        }
    }
}

/**
 * Get the item as the auction winner
 */
class ReceiveItemAsWinnerHuman extends CyclicBehaviour {

    private BidderHuman myAgent;

    public ReceiveItemAsWinnerHuman(BidderHuman agent) {
        super(agent);
        myAgent = agent;
    }

    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
        ACLMessage msg = myAgent.receive(mt);
        if (msg != null) {
            // ACCEPT_PROPOSAL Message received. Process it
            String ans = msg.getContent();
            String[] parts = ans.split(",");
            String itemName = parts[0];
            int price = Integer.parseInt(parts[1]);
            ACLMessage reply = msg.createReply();

            reply.setPerformative(ACLMessage.INFORM);
            System.out.println("Congratulations! You have won the auction");
            System.out.println(itemName+" is now yours! With the price " + price);

            myAgent.send(reply);

            // Cut money from budget
            myAgent.budget -= price;            
            myAgent.refreshGUI();
        }
        else {
            block();
        }
    }
}

/**
 * Process CFP as a computer, whether to bid on that item or not
 * All-in strategy. Bid with all the money it has.
 */
class ReceiveINFORMHuman extends CyclicBehaviour {
    public void action() {
        MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
        ACLMessage msg = myAgent.receive(mt);
        if (msg != null) {
            // INFORM Message received. Print it.
            System.out.println(msg.getContent());
        }
        else {
            block();
        }
    }
}
