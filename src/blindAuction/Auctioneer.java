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
 * JADE agent representing an auctioneer of an auction.
 * It has single sequential behavior representing its lifecycle.
 */
public class Auctioneer extends Agent {

	// The catalogue of items for sale (maps the title of a item to its price)
	private Hashtable catalogue;

	// The GUI by means of which the user can add items in the catalogue
	private AuctioneerGUI myGui;

    // Show whether auction has started
    private boolean auctionStarted = false;

    // The template to receive replies
    public MessageTemplate mt; 

    // The list of known bidders
	public AID[] bidders;

    // The bidder who provides the best offer
    public AID bestBidder;

    // The most updated best offered price
    public int bestPrice;

    public boolean biddersFound = false;
    public boolean CFPSent = false;
    public boolean bidsReceived = false;

    public FindBidder p = null;
    public SendCFP q = null;
    public ReceiveBids r = null;
    public AnnounceWinnerAndUpdateCatalogue s = null;

    @Override
    protected void setup() {

        // Printout a welcome message
		System.out.println("Hello! Auctioneer "+getAID().getName()+" is ready");

		// Create the catalogue
		catalogue = new Hashtable();
        updateCatalogue("ABC",1200);

		// Create and show the GUI 
		myGui = new AuctioneerGUI(this);
		myGui.showGui();
       
        // Add a TickerBehaviour that schedules a request to bidders every minute
        addBehaviour(new ActionPerMinute(this));
    }

	// Put agent clean-up operations here
	protected void takeDown() {
		// Close the GUI
		myGui.dispose();

		// Printout a dismissal message
		System.out.println("Auctioneer "+getAID().getName()+" terminating");
	}

	/**
       This is invoked by the GUI when the user adds a new item for sale
    */
	public void updateCatalogue(final String title, final int price) {

		addBehaviour(
                     new OneShotBehaviour() {
                         public void action() {
                             catalogue.put(title, new Integer(price));
                             System.out.println(title+" is inserted into catalogue. Initial Price = "+price);
                         }
                     }
                     );
	}

	/**
       This is invoked to delete item
    */
	public Integer removeItemFromCatalogue(final String title) {

        Integer price = null;
        price = (Integer) catalogue.get(title);
		addBehaviour(
                     new OneShotBehaviour() {
                         public void action() {
                             catalogue.remove(title);
                         }
                     }
                     );
        return price;
	}

    public boolean isCatalogueEmpty() {
        return catalogue.isEmpty();
    }

    public String getFirstItemName() {
        return (String)catalogue.keySet().toArray()[0];
    }

    public int getItemInitialPrice(final String title) {
        Integer price = (Integer) catalogue.get(title);
        if (price != null) {
            return (int)price;
        }
        else {
            return 0;
        }
    }

}

// Add a TickerBehaviour that schedules an auction to bidders every minute
class ActionPerMinute extends TickerBehaviour {

    private Auctioneer myAgent;

    private String currentItemName;

    public ActionPerMinute(Auctioneer agent) {
        super(agent, 10000);
        myAgent = agent;
    }

    @Override
    protected void onTick(){

        // Initialize all conditions
        myAgent.biddersFound = false;
        myAgent.CFPSent = false;
        myAgent.bidsReceived = false;
        
        // If there is any item to sell
        if (!myAgent.isCatalogueEmpty()) {

            if (myAgent.p != null) myAgent.removeBehaviour(myAgent.p);
            if (myAgent.q != null) myAgent.removeBehaviour(myAgent.q);
            if (myAgent.r != null) myAgent.removeBehaviour(myAgent.r);
            if (myAgent.s != null) myAgent.removeBehaviour(myAgent.s);

            currentItemName = myAgent.getFirstItemName();
            System.out.println("Starting auction for item " + currentItemName);

            System.out.println("Waiting for bidders.. ");

            // Find Bidder
            myAgent.p = new FindBidder(myAgent);
            myAgent.addBehaviour(myAgent.p);
                    
            // Send CFP to all bidders
            myAgent.q = new SendCFP(myAgent, currentItemName, myAgent.getItemInitialPrice(currentItemName));
            myAgent.addBehaviour(myAgent.q);

            // Receive all proposals/refusals from bidders and find the highest bidder
            myAgent.r = new ReceiveBids(myAgent);
            myAgent.addBehaviour(myAgent.r);
            
            // Send the request order to the bidder that provided the best offer
            myAgent.s = new AnnounceWinnerAndUpdateCatalogue(myAgent, currentItemName);
            myAgent.addBehaviour(myAgent.s);
        }        
        else {
			System.out.println("Please add an item before we can commence auctions");
        }
    }
}

class FindBidder extends Behaviour {

    private Auctioneer myAgent;

    private boolean noBidder = false;
    private boolean oneBidder = false;

    public FindBidder(Auctioneer agent) {
        super(agent);
        myAgent = agent;
    }
    
    public void action() {

        if (!myAgent.biddersFound) {

            // Update the list of bidders
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("blind-auction");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template); 
                if (result.length > 0) {
                    System.out.println("Found the following " + result.length +" bidders:");
                    myAgent.bidders = new AID[result.length];
                    for (int i = 0; i < result.length; ++i) {
                        myAgent.bidders[i] = result[i].getName();
                        System.out.println(myAgent.bidders[i].getName());
                    }
                    myAgent.biddersFound = true;                    
                }
            }
            catch (FIPAException fe) {
                fe.printStackTrace();
            }

        }
    }

    public boolean done() {
        return myAgent.biddersFound;
    }
}

/**
   Send CFP to all bidders
*/
class SendCFP extends Behaviour {

    private Auctioneer myAgent;
    private String itemName;
    private int itemInitialPrice;

    public SendCFP(Auctioneer agent, String itemName, int itemInitialPrice) {
        super(agent);
        myAgent = agent;
        this.itemName = itemName;
        this.itemInitialPrice = itemInitialPrice;
    }
    
    public void action() {

        if (!myAgent.CFPSent && myAgent.biddersFound) {

            // Send the cfp to all bidders
            System.out.println("Sending CFP to all bidders..");
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (int i = 0; i < myAgent.bidders.length; ++i) {
                cfp.addReceiver(myAgent.bidders[i]);
            } 
            cfp.setContent(this.itemName + "," + this.itemInitialPrice);
            cfp.setConversationId("blind-bid");
            cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
            myAgent.send(cfp);

            // Prepare message template
            myAgent.mt = MessageTemplate.and(MessageTemplate.MatchConversationId("blind-bid"),
                                             MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
        
            myAgent.CFPSent = true;

        }
    }

    public boolean done() {
        return myAgent.CFPSent;
    }
}

/**
   Receive all proposals/refusals from bidders and find the highest bidder
*/
class ReceiveBids extends Behaviour {

    private Auctioneer myAgent;

    private int repliesCnt = 0; // The counter of replies from seller agents

    public ReceiveBids(Auctioneer agent) {
        super(agent);
        myAgent = agent;
    }
    
    public void action() {

        if (myAgent.CFPSent) {

            // Receive all proposals/refusals from seller agents
            ACLMessage msg = myAgent.receive(myAgent.mt);
            if (msg != null) {
                // Bid received
                if (msg.getPerformative() == ACLMessage.PROPOSE) {

                    // This is an offer 
                    int price = Integer.parseInt(msg.getContent());
                    if (myAgent.bestBidder == null || price > myAgent.bestPrice) {
                        // This is the best offer at present
                        myAgent.bestPrice = price;
                        myAgent.bestBidder = msg.getSender();
                    }

                    // Inform the bidder that the bid is received
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("Your bid is received");
                    myAgent.send(reply);
                }

                if (msg.getPerformative() == ACLMessage.REFUSE){
                    System.out.println(msg.getSender().getLocalName() + " is not joining this auction");
                }

                repliesCnt++;
            }
            else {
                block();
            }
        
            if (repliesCnt >= myAgent.bidders.length) {
                // We have received all bids
                myAgent.bidsReceived = true;
            }

        }
    }

    public boolean done() {
        return myAgent.bidsReceived;
    }
}

/**
 * Send the request order to the bidder that provided the best offer
 * @condition: if there is any winner
 */
class AnnounceWinnerAndUpdateCatalogue extends Behaviour {

    private Auctioneer myAgent;

    private String itemName;

    private boolean isDone = false;

    public AnnounceWinnerAndUpdateCatalogue(Auctioneer agent, String itemName) {
        this.itemName = itemName;
        myAgent = agent;
    }
    
    public void action() {

        if (myAgent.bidsReceived) {

            if (myAgent.bestPrice >= myAgent.getItemInitialPrice(this.itemName)){
                // Send the purchase order to the seller that provided the best offer
                ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                order.addReceiver(myAgent.bestBidder);
                order.setContent(this.itemName + "," + myAgent.bestPrice);
                order.setConversationId("blind-bid");
                order.setReplyWith("order"+System.currentTimeMillis());

                System.out.println("Announcing Winner for " + this.itemName);        

                Integer price = (Integer) myAgent.removeItemFromCatalogue(this.itemName);
                if (price != null) {
                    System.out.println(itemName+" sold to agent "+ myAgent.bestBidder.getName());
                }
                else {
                    // The requested item has been sold to another buyer..somehow
                    order.setPerformative(ACLMessage.FAILURE);
                    order.setContent("not-available");
                }
                myAgent.send(order);

                // Re-Initialize all conditions
                myAgent.biddersFound = false;
                myAgent.CFPSent = false;
                myAgent.bidsReceived = false;                
                myAgent.bestPrice = 0;
                myAgent.bestBidder = null;
            }
            else {
                System.out.println("No winner.. Bids were insufficient");
            }
            isDone = true;
        }
    }

    public boolean done() {
        return isDone;
    }
}
