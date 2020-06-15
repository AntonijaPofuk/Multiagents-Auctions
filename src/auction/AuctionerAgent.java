package auction;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author: Antonija Pofuk
 */
public class AuctionerAgent extends Agent {

    private AID[] bidderAgents;

    private String itemName;
    private Integer itemPrice;
    
    @Override
    protected void setup() {                                
        System.out.println("*********************Auction is ready!*********************");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            itemName = (String) args[0];
            itemPrice = Integer.parseInt((String) args[1]);
            System.out.println("Name of item: \"" + itemName + "\". Price: " + itemPrice);
            System.out.println("Place bids...");

            addBehaviour(new OneShotBehaviour() {
                @Override
                public void action() {
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("auction-bidder");
                    template.addServices(sd);                    
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        bidderAgents = new AID[result.length];
                        System.out.println("Found agents:");
                        for (int i = 0; i < result.length; i++) {
                            System.out.println("Agent " + result[i].getName());
                            bidderAgents[i] = result[i].getName();
                        }
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }
                    myAgent.addBehaviour(new AuctionPerformer());
                }
            });
        } else {
            System.out.println("No item to be auctioned this time");
            doDelete();
        }
    }

    private class AuctionPerformer extends Behaviour {
        private int step = 0;
        private Map<AID, Integer> receivedProposals = new HashMap<AID, Integer>();
        private int numExpectedProposals = 0;
        private MessageTemplate mt;
        private AID highestBidder = null;
        private int highestBid = 0;
        private int roundsWithNoOffers = 0;
        @Override
        public void action() {
            switch (step) {
                case 0:
                    receivedProposals = new HashMap<AID, Integer>();
                    numExpectedProposals = 0;
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < bidderAgents.length; i++) {
                        if (highestBidder == null || (highestBidder != null && bidderAgents[i].compareTo(highestBidder) != 0)) {
                            cfp.addReceiver(bidderAgents[i]);
                            numExpectedProposals++;
                        }
                    }
                    if (highestBidder != null) {
                        cfp.setContent(itemName + "-" + highestBid + "-" + 0);
                    } else {
                        cfp.setContent(itemName + "-" + itemPrice + "-" + 0);
                    }
                    cfp.setConversationId("auction");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis());
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(
                            MessageTemplate.MatchConversationId("auction"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        switch (reply.getPerformative()) {
                            case ACLMessage.PROPOSE:
                                receivedProposals.put(reply.getSender(), Integer.parseInt(reply.getContent()));
                                System.out.println(reply.getSender().getName() + " bids " + reply.getContent());
                                roundsWithNoOffers = 0;
                                break;
                            case ACLMessage.REFUSE:
                                receivedProposals.put(reply.getSender(), null);
                                roundsWithNoOffers++;
                                break;
                        }
                        if (receivedProposals.size() == numExpectedProposals) {
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    Iterator<Map.Entry<AID, Integer>> iter = receivedProposals.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<AID, Integer> item = iter.next();
                        if (item.getValue() != null && highestBid < item.getValue()) {
                            highestBidder = item.getKey();
                            highestBid = item.getValue();
                        }
                    }
                    if (highestBidder != null) {
                        System.out.println("Highest bid so far: " + highestBid + " for " + highestBidder.getLocalName().toUpperCase());
                    } else {
                        System.out.println("Only received invalid bids!");
                    }
                    ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept.addReceiver(highestBidder);
                    accept.setContent(itemName + "-" + highestBid + "-" + 0);
                    accept.setConversationId("auction");
                    accept.setReplyWith("bid-ok" + System.currentTimeMillis());
                    myAgent.send(accept);
                    receivedProposals.keySet().stream()
                            .filter(aid -> aid != highestBidder && receivedProposals.get(aid) != null)
                            .forEach(aid -> {
                                ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                reject.addReceiver(highestBidder);
                                reject.setContent(itemName + "-" + receivedProposals.get(aid) + "-" + 0);
                                reject.setConversationId("auction");
                                reject.setReplyWith("bid-reject" + System.currentTimeMillis());
                                myAgent.send(reject);
                            });

                    step = 3;
                    break;
                case 3:
                    if (roundsWithNoOffers != 0) {
                        System.out.println(highestBid + " " + roundsWithNoOffers);
                    }
                    if (roundsWithNoOffers == 3) {
                        step = 4;
                    } else {
                        step = 0;
                    }
                    break;
                case 4:
                    System.out.println("Sold to: " + highestBidder.getName() + " for " + highestBid + " points.");
                    cfp = new ACLMessage(ACLMessage.CFP);
                    cfp.addReceiver(highestBidder);
                    cfp.setContent("Content: " + itemName + "-" + highestBid + "-" + highestBid);
                    cfp.setConversationId("auction");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis());
                    myAgent.send(cfp);
                    mt = MessageTemplate.and(
                            MessageTemplate.MatchConversationId("auction"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 5;
                    break;
            }
        }
        @Override
        public boolean done() {
            return (step == 5);
        }
    }
}
