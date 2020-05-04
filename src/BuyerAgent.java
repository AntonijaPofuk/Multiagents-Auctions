package auction;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.util.concurrent.ThreadLocalRandom;

public class BuyerAgent extends Agent {

    private String targetItemTitle;
    private int budget;
    private AID[] sellerAgents;
    private int bid;

    // agent initialization 
    protected void setup() {
        setBudget();
        System.out.println("Buyer agent " + getAID().getName() + " is ready.");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            targetItemTitle = (String) args[0];
            System.out.println("Target item is \"" + targetItemTitle + "\". Buyer has budget: " + budget + " points.");

            //TickerBehaviour that schedules a request to seller agents every minute
            addBehaviour(new TickerBehaviour(this, 6000) {
                protected void onTick() {
                    System.out.println("Trying to buy " + targetItemTitle);
                    // Update the list of seller 
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("item-selling");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        System.out.println("Found the following seller agents:");
                        sellerAgents = new AID[result.length];
                        if (result.length > 0) {
                            for (int i = 0; i < result.length; ++i) {
                                sellerAgents[i] = result[i].getName();
                                System.out.println(sellerAgents[i].getName());
                            }
                        } else {
                            System.out.println("There are currently no available seller agents.");
                        }
                    } catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    // Perform the request
                    myAgent.addBehaviour(new RequestPerformer());
                }
            });
        } else {
            // Make the agent terminate
            System.out.println("No target item specified");
            doDelete();
        }
    }

    private void setBudget() {
        int min = 10;
        int max = 1000;
        budget = ThreadLocalRandom.current().nextInt(min, max);
    }

    private void getBid() {
        int min = 10;
        int max = 100;
        bid = ThreadLocalRandom.current().nextInt(min, max);
        System.out.println("Bid: "+bid+" points");
    }

    // Put agent clean-up operations here
    protected void takeDown() {
        System.out.println("Buyer agent " + getAID().getName() + " is stepping away.");
    }

    /**
     * Inner class RequestPerformer. This is the behaviour used by Item-buyer
     * agents to request seller agents the target item.
     */
    private class RequestPerformer extends Behaviour {

        private AID bestSeller; // The agent who provides the best offer 
        private int bestPrice;  // The best offered price
        private int repliesCnt = 0; // The counter of replies from seller agents
        private MessageTemplate mt; // The template to receive replies
        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    // Send the cfp to all sellers
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < sellerAgents.length; ++i) {
                        cfp.addReceiver(sellerAgents[i]);
                    }
                    cfp.setContent(targetItemTitle);
                    cfp.setConversationId("item-trade");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    // Prepare the template to get proposals
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("item-trade"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;
                case 1:
                    // Receive all proposals/refusals from seller agents
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            // This is an offer 
                            int price = Integer.parseInt(reply.getContent());
                            if (bestSeller == null || price < bestPrice) {
                                // Best offer
                                if (price < budget) {
                                    bestPrice = price;
                                    bestSeller = reply.getSender();
                                    getBid();
                                    price = price + bid;
                                } //Avalabile price to buyers budget
                                if (price > budget) {
                                    System.out.println("Attempt failed: " + targetItemTitle + " not available for sale with " + getAID().getName() + "'s budget (" + budget + ")");
                                }
                            }
                        }
                        repliesCnt++;
                        if (repliesCnt >= sellerAgents.length) {
                            // We received all replies
                            step = 2;
                        }
                    } else {
                        block();
                    }
                    break;
                case 2:
                    // Send the purchase order to the seller that provided the best offer
                    ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    order.addReceiver(bestSeller);
                    order.setContent(targetItemTitle);
                    order.setConversationId("item-trade");
                    order.setReplyWith("order" + System.currentTimeMillis());
                    myAgent.send(order);
                    // Prepare the template to get the purchase order reply
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("item-trade"),
                            MessageTemplate.MatchInReplyTo(order.getReplyWith()));
                    step = 3;
                    break;
                case 3:
                    // Receive the purchase order reply
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        // Purchase order reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            // Purchase successful. We can terminate
                            System.out.println(targetItemTitle + " successfully purchased from agent " + reply.getSender().getName());
                            System.out.println("Price = " + bestPrice);
                            budget = budget - bestPrice;
                            System.out.println("Budget = " + budget);
                            myAgent.doDelete();
                        } else {
                            System.out.println("Attempt failed: requested item already sold.");
                        }

                        step = 4;
                    } else {
                        block();
                    }
                    break;
            }
        }

        public boolean done() {
            if (step == 2 && bestSeller == null) {
                System.out.println("No avaliable sellers");
            }
            return ((step == 2 && bestSeller == null) || step == 4);
        }
    }
}
