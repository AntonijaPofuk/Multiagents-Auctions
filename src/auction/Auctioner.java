package auction;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;


public class Auctioner extends Agent{
	
	private AID[] kupci;
    private String nazivPredmeta;
    private Integer cijenaPredmeta;

    @Override
    protected void setup() {
        System.out.println("Aukcionar pokre�e aukciju!");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            nazivPredmeta = (String) args[0];
            cijenaPredmeta = Integer.parseInt((String) args[1]);
            System.out.println("Prodaje se \"" + nazivPredmeta + "\" s po�etnom cijenom " + cijenaPredmeta + " kn");
            System.out.println("Mo�ete dati svoje ponude!");

            addBehaviour(new OneShotBehaviour() {
    	   		@Override
                public void action() {

                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("kupac");
                    template.addServices(sd);

                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);

                        kupci = new AID[result.length];
                        for (int i = 0; i < result.length; i++) {
                            //System.out.println("Na�en kupac: " + result[i].getName());
                            kupci[i] = result[i].getName();
                        }
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }
                    

                    myAgent.addBehaviour(new Performer());
                }
            });
        
        } else {
            System.out.println("Nema stvari na aukciji.");
            doDelete();
        }
    }

    private class Performer extends Behaviour {
        private int step = 0;
        private Map<AID, Integer> ponude = new HashMap<>();
        private int num = 0;
        private MessageTemplate mt;
        private AID maxKupac = null;
        private int maxPonuda = 0;
        private int krugoviBezPonuda = 0;

        @Override
        public void action() {
            switch (step) {
                case 0:
                    ponude = new HashMap<>();
                    num = 0;
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);

                    for (int i = 0; i < kupci.length; i++) {
                        if (maxKupac == null || (maxKupac != null && kupci[i].compareTo(maxKupac) != 0)) {
                            cfp.addReceiver(kupci[i]);
                            num++;
                        }
                    }

                    if (maxKupac != null) {
                        cfp.setContent(nazivPredmeta + "||" + maxPonuda);
                    } else {
                        cfp.setContent(nazivPredmeta + "||" + cijenaPredmeta);
                    }

                    cfp.setConversationId("aukcija");
                    cfp.setReplyWith("cfp" + System.currentTimeMillis());

                    myAgent.send(cfp);

                    mt = MessageTemplate.and(
                            MessageTemplate.MatchConversationId("aukcija"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

                    step = 1;
                    break;
                    
                case 1:
                    ACLMessage reply = myAgent.receive(mt);

                    if (reply != null) {
                        switch (reply.getPerformative()) {
                            case ACLMessage.PROPOSE:
                                ponude.put(reply.getSender(), Integer.parseInt(reply.getContent()));
                                System.out.println(reply.getSender().getName() + " nudi " + reply.getContent() + " kn");
                                krugoviBezPonuda = 0;
                                break;
                                
                            case ACLMessage.REFUSE:
                                ponude.put(reply.getSender(), null);
                                krugoviBezPonuda++;
                                break;
                        }

                        if (ponude.size() == num) {
                            step = 2;
                        }

                    } else {
                        block();
                    }
                    break;
                    
                case 2:
                    Iterator<Map.Entry<AID, Integer>> iterator = ponude.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<AID, Integer> item = iterator.next();
                        if (item.getValue() != null && maxPonuda < item.getValue()) {
                            maxKupac = item.getKey();
                            maxPonuda = item.getValue();
                        }
                    }

                    if (maxKupac != null) {
                        System.out.println("Najvi�a ponu�ena cijena: " + maxPonuda + " kn od " + maxKupac.getName());
                    } else {
                        System.out.println("Primljene samo neodgovaraju�e ponude!");
                    }

                    ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                    accept.addReceiver(maxKupac);
                    accept.setContent(nazivPredmeta + "||" + maxPonuda);
                    accept.setConversationId("aukcija");
                    accept.setReplyWith("ponuda-ok" + System.currentTimeMillis());
                    myAgent.send(accept);


                    ponude.keySet().stream()
                            .filter(aid -> aid != maxKupac && ponude.get(aid) != null)
                            .forEach(aid -> {
                                ACLMessage reject = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                                reject.addReceiver(maxKupac);
                                reject.setContent(nazivPredmeta + "||" + ponude.get(aid));
                                reject.setConversationId("aukcija");
                                reject.setReplyWith("ponuda-odbijena" + System.currentTimeMillis());

                                myAgent.send(reject);
                            });

                    step = 3;
                    break;
                    
                case 3:

                    System.out.println("Javlja li se netko za " + (maxPonuda * 1.2) + " kn?");

                    if (krugoviBezPonuda != 0) {
                        System.out.println(maxPonuda + " kn " + krugoviBezPonuda + ". put");
                    }

                    if (krugoviBezPonuda == 3) {
                        step = 4;
                    } else {
                        step = 0;
                    }
                    break;
                    
                case 4:
                    System.out.println("Prodano kupcu " + maxKupac.getName() + " za " + maxPonuda + " kn.");

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
