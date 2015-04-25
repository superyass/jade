package bookTrading;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Scanner;

public class Buyer extends Agent {

	private ArrayList<String> books = new ArrayList<String>();
	private AID[] sellerAgents;

	@Override
	protected void setup() {
		super.setup();

		if (getArguments() != null && getArguments().length > 0) {
			for (Object o : getArguments()) {
				books.add((String) o);
			}
			
			System.out.println(getLocalName()+"  SETUP: Books a acheter: " + books.size());
			double d = Math.random()*10000;
			try {
				Thread.sleep((long) d);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			addBehaviour(new RequestPerformer());

		} else {
			System.out.println(getLocalName()+" SETUP: Pas de book en entrée");
			doDelete();
		}

	}// fin setup

	private class RequestPerformer extends Behaviour {

		Boolean isFinished = false;
		int step = 0;
		MessageTemplate mt;
		Hashtable<String, Integer> bookBestPrice = new Hashtable<String, Integer>();
		Hashtable<String, AID> bookBestSeller = new Hashtable<String, AID>();
		private int repliesCnt;

		@Override
		public void action() {
			switch (step) {
			case 0:
				// init
				bookBestPrice = new Hashtable<String, Integer>();
				bookBestSeller = new Hashtable<String, AID>();
				repliesCnt = 0;

				System.out.println(getLocalName()+" : Recherche des Sellers *******");
				
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("book-selling");
				template.addServices(sd);
				
				try {
					DFAgentDescription[] result = DFService.search(myAgent, template);
					sellerAgents = new AID[result.length];
					
					System.out.println(getLocalName()+" : seller agents trouvés sont:");
					
					for (int i = 0; i < result.length; ++i) {
						sellerAgents[i] = result[i].getName();
						System.out.println("   "+getLocalName()+" =>" + sellerAgents[i].getLocalName());
					}
				} catch (FIPAException fe) {
					fe.printStackTrace();
				}
				step = 1;
//				System.out.println(getLocalName()+" 0->1");
				break;
			case 1:
				// envoie des msg aux Sellers avec les noms des books
				System.out.println(getLocalName()+"  ETAPE1: Envoie de messages aux agents avec la liste des livres*******");
				
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < sellerAgents.length; ++i) {
					cfp.addReceiver(sellerAgents[i]);
				}

				//construison la liste des book
				String listeBooks = "";
				for (String book : books) {
					listeBooks = listeBooks + book + ",";
				}

				// supprimer la derniere virgule
				listeBooks = listeBooks.substring(0, listeBooks.length() - 1);
				cfp.setContent(listeBooks);

				cfp.setConversationId("book-trade");

				myAgent.send(cfp);
				
				step = 2;
//				System.out.println(getLocalName()+" 1->2");
				break;
			case 2:
				// Receive all proposals/refusals from seller agents

				// Prepare the template to get proposals
				mt = MessageTemplate.MatchConversationId("book-trade");
				ACLMessage reply = myAgent.receive(mt);
				
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer
						// recuperer quel book et le prix proposé
						String txt = reply.getContent();
						Scanner scanner = new Scanner(txt);
						scanner.useDelimiter(",");
						String book = scanner.next();
						int price = scanner.nextInt();
						System.out.println(getLocalName()+"  ETAPE2: offre recu titre:" + book + " prix: " + price + ", Seller:" + reply.getSender().getLocalName());

						if (bookBestPrice.get(book) == null) {
							// book n existe pas on l ajoute
							bookBestPrice.put(book, price);
							bookBestSeller.put(book, reply.getSender());
						} else if (price < bookBestPrice.get(book)) {
							//on a trouve a seller moin chere pour book
							bookBestPrice.put(book, price);
							bookBestSeller.put(book, reply.getSender());
						}

					}
					repliesCnt++;
				} else {
//					System.out.println(getLocalName()+" etape2 blocké");
					block();
				}
				// chaque seller renvoie n fois de proposal,n=nombre de book
				if (repliesCnt >= sellerAgents.length * books.size()) {
					// We received all replies
					System.out.println(getLocalName()+" 2->3");
					step = 3;
				}
				break;
			case 3:
				// test si on a des offres positives pr tous les livres
				if (bookBestSeller.size() == books.size()) {
					System.out.println(getLocalName()+"  ETAPE3: Tous les book ont été trouvés, essayons de les achetes*******");
					step = 4;
//					System.out.println(getLocalName()+" 3->4");
				} else {
					System.out.println(getLocalName()+"  ETAPE3: Tous les book n'ont pas été trouvés (" + bookBestPrice.size() + " trouvé sur " + books.size()
							+ "). essayons a nouveau!*******");
					//on revient pr trouvé d autres seller peut etre :p
					step = 0;
//					System.out.println(getLocalName()+" 3->0");
					System.out.println(getLocalName()+" 3: PAUSE de 10s");
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

				}
				break;
			case 4:
				// Send the purchase order to the seller that provided the best
				// offer
				System.out.println(getLocalName()+" 4: Envoie des achats proposals*******");
				for (String book : books) {
					ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
					order.addReceiver(bookBestSeller.get(book));
					order.setContent(book);
					order.setConversationId("book-trade");
					order.setReplyWith("order" + System.currentTimeMillis());
					myAgent.send(order);
				}
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.MatchConversationId("book-trade");

				//PAUSE avant de recevoir
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				repliesCnt=0;
				step = 5;
//				System.out.println(getLocalName()+" 4->5");
				break;
			case 5:
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
//				System.out.println(getLocalName()+"  5");

				if (reply != null) {
					
					String titre = reply.getContent();
					boolean flag = true;
					
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						repliesCnt++;
						// Purchase successful. We can terminate
						System.out.println(getLocalName()+" "+titre + " successfully purchased from agent " + reply.getSender().getLocalName());
						System.out.println(getLocalName()+" Price = " + bookBestPrice.get(titre));
						
					} else if (reply.getPerformative() == ACLMessage.FAILURE) {
						System.out.println(getLocalName()+" Attempt failed: requested book already sold.");
						myAgent.doDelete();
						System.out.println(getLocalName()+" --FIN--");
						isFinished = true;
					}

					if (repliesCnt == books.size()) {
						if (flag == true)
							System.out.println(getLocalName()+" ********Tous les books ont été achetés********");
						else
							System.out.println(getLocalName()+" ********Tous les book n'ont pas été achetés!!!********");

						myAgent.doDelete();
						System.out.println(getLocalName()+" --FIN--");
						isFinished = true;
					}

				} else {
//					System.out.println(getLocalName()+" ETAPE 5 blocké");
					block();
				}
				break;
			}
		}

		@Override
		public boolean done() {
			return isFinished;
		}

	}

}
