package auction;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author: Antonija Pofuk
 */
public class BidderAgent extends Agent {

    private int wallet;
    private int randomBid;

    @Override
    protected void setup() {

        setRandomWallet();
        addBehaviour(new BidRequestsServer());
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("auction-bidder");
        sd.setName("MultiAgentSystem-auctions");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println(getName() + " is ready. My budget:" + wallet);
    }

    private void setRandomWallet() {
        int min = 10;
        int max = 1000;
        wallet = ThreadLocalRandom.current().nextInt(min, max);
    }

    private void setRandomBid() {
        int min = 10;
        int max = 50;
        randomBid = ThreadLocalRandom.current().nextInt(min, max);
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("Agent " + getAID().getName() + "is stepping away...");
    }

    private class BidRequestsServer extends Behaviour {

        private String itemName;
        private Integer itemPrice;
        private String itemWinPrice;

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive();

            if (wallet <= 0) {
                System.out.println("No budget left...");
                myAgent.doDelete();
            }
            if (msg != null) {
                parseContent(msg.getContent());
                ACLMessage reply = msg.createReply();
                int bid;
                if (itemPrice < (wallet)) {
                    setRandomBid();
                    bid = (int) (itemPrice + randomBid);
                    System.out.println("Bid for " + myAgent.getLocalName() + " is: " + bid + " points.");
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(bid));
                                        
                    if (!itemWinPrice.equalsIgnoreCase("0")) {                    
                        wallet = wallet - itemPrice;
                        System.out.println(myAgent.getLocalName() + ": Preostalo mi je " + wallet);
                    }
                                        
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                    System.out.println(myAgent.getLocalName() + " doesnt have enough budget to bid. ---BUDGET: (" + wallet + ")---");
                }
                myAgent.send(reply);
                System.out.println(itemWinPrice);

            } else {
                block();
            }
        }

        private void parseContent(String content) {
            String[] split = content.split("-");
            itemName = split[0];
            itemPrice = Integer.parseInt(split[1]);
            itemWinPrice = split[2];
        }

        @Override
        public boolean done() {
            return false;
        }
    }
}
