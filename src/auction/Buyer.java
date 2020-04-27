package auction;

import java.util.concurrent.ThreadLocalRandom;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class Buyer extends Agent{
	
	private int novac;

    @Override
    protected void setup() {

        setRandNovac();
        addBehaviour(new Requests());

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("kupac");
        sd.setName("Aukcija-antikviteti");
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        
        System.out.println(getAID().getName() + " �eli ne�to kupiti. - Imam novaca: " + novac + " kn");
    }

    private void setRandNovac() {
        int min = 1000;
        int max = Integer.MAX_VALUE;

        novac = ThreadLocalRandom.current().nextInt(min, max);
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        System.out.println("Kupac " + getAID().getName() + " zavr�ava.");
    }

    
    private class Requests extends Behaviour {
    	
        private String nazivPredmeta;
        private Integer cijenaPredmeta;

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive();

            if (msg != null) {
                parseContent(msg.getContent());

                ACLMessage reply = msg.createReply();
                int kupac;

                if (cijenaPredmeta < novac / 4) {
                	// 5-10 posto ve�a nova cijena
                    kupac = (int) (cijenaPredmeta + cijenaPredmeta * ((float) ThreadLocalRandom.current().nextInt(5, 10) / 10));
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setContent(String.valueOf(kupac));
                } else {
                    reply.setPerformative(ACLMessage.REFUSE);
                }

                myAgent.send(reply);
            } else {
                block();
            }
        }

        private void parseContent(String content) {
            String[] split = content.split("\\|\\|");

            nazivPredmeta = split[0];
            cijenaPredmeta = Integer.parseInt(split[1]);
        }

        @Override
        public boolean done() {
            return false;
        }
    }

}
