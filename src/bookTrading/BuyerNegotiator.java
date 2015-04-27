package bookTrading;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.Scanner;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.sun.org.apache.bcel.internal.generic.GOTO;

public class BuyerNegotiator extends Agent {

	private ArrayList<String> books = new ArrayList<String>();
	private AID[] sellerAgents;
	private int plafond = 0;	// 0 = pas de plafond, ce parametre doit etre entré sous la forme "PLAFOND:X" , ou X est la valeur du plafond maximal d'achat
	
	@Override
	protected void setup() {
		super.setup();

		if (getArguments() != null && getArguments().length > 0) {
			for (Object o : getArguments()) {
				String s = (String) o;
				if(s.contains("PLAFOND:")){
					s=s.replace("PLAFOND:", "");
					System.out.println(s);
					plafond = Integer.parseInt(s);
				}else{
					books.add(s);					
				}
			}

			System.out.println(getLocalName() + "  SETUP: Books a acheter: " + books.size());
			double d = Math.random() * 10000;
			
			try {
				Thread.sleep((long) d);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			addBehaviour(new RequestPerformer());

		} else {
			System.out.println(getLocalName() + " SETUP: Pas de book en entrée");
			doDelete();
		}

	}// fin setup

	private class RequestPerformer extends Behaviour {

		Boolean isFinished = false;
		int step = 0;
		MessageTemplate mt;
		Hashtable<String, Integer> bookBestPrice = new Hashtable<String, Integer>();
		Hashtable<String, AID> bookBestSeller = new Hashtable<String, AID>();

		// HashMap avec 2 keys & 1 value de l api Guava de google,
		Table<AID, String, Integer> sellerBookPriceNegociation = HashBasedTable.create();
		private int repliesCnt;
		Random rand = new Random();

		@Override
		public void action() {
			switch (step) {
			case 0:
				// init
				bookBestPrice = new Hashtable<String, Integer>();
				bookBestSeller = new Hashtable<String, AID>();
				repliesCnt = 0;

				System.out.println(getLocalName() + " : Plafond d'achat: "+plafond);
				System.out.println(getLocalName() + " : Recherche des Sellers");

				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("book-selling");
				template.addServices(sd);

				try {
					DFAgentDescription[] result = DFService.search(myAgent, template);
					sellerAgents = new AID[result.length];

					System.out.println(getLocalName() + " : seller agents trouvés sont:");

					for (int i = 0; i < result.length; ++i) {
						sellerAgents[i] = result[i].getName();
						System.out.println("   " + getLocalName() + " =>" + sellerAgents[i].getLocalName());
					}
				} catch (FIPAException fe) {
					fe.printStackTrace();
				}
				step = 1;
				// System.out.println(getLocalName()+" 0->1");
				break;
			case 1:
				// envoie des msg aux Sellers avec les noms des books
				System.out.println(getLocalName() + "  ETAPE1: Envoie de messages aux agents avec la liste des livres");

				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < sellerAgents.length; ++i) {
					cfp.addReceiver(sellerAgents[i]);
				}

				// construison la liste des book
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
				// System.out.println(getLocalName()+" 1->2");
				break;
			case 2:
				// negociation
				// Receive all proposals/refusals from seller agents
				// Prepare the template to get proposals
				mt = MessageTemplate.MatchConversationId("negociation");
				ACLMessage reply = myAgent.receive(mt);

				if (reply != null) {
					// ACCEPT: si on a proposé un prix et le seller a accepté
					// PROPOSE: dans le cas ou le seller a proposé apres le CFP,
					// ou apres qu il a rejeté notre offre, dans ce cas sa
					// proposition est final
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer
						// recuperer quel book et le prix proposé
						String txt = reply.getContent();
						Scanner scanner = new Scanner(txt);
						scanner.useDelimiter(",");
						String book = scanner.next();
						int price = scanner.nextInt();

						//on est dans le 1er propsal
						if (sellerBookPriceNegociation.get(reply.getSender(), book) == null) { 

							System.err.println(getLocalName() + "[Negociation]: Permiere proposition reçu pour book: " + book + " de "
									+ reply.getSender().getLocalName() + " prix: " + price);

							int negociationPrice = randInt((int) (price / 2), price);

							//alors on a "decidé" de negociatier
							if (negociationPrice != price) { 

								sellerBookPriceNegociation.put(reply.getSender(), book, negociationPrice);

								// on refuse la proposition
								ACLMessage refus = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
								refus.setContent(book + "," + price);
								refus.addReceiver(reply.getSender());
								refus.setConversationId("negociation");

								// on envoie notre proposition
								ACLMessage proposition = new ACLMessage(ACLMessage.PROPOSE);
								proposition.setContent(book + "," + negociationPrice);
								proposition.addReceiver(reply.getSender());
								proposition.setConversationId("negociation");
								
								System.err.println(getLocalName() + "[Negociation]: Une proposition a été envoye pour le book: " + book + " a "
										+ reply.getSender().getLocalName() + " prix de negociation: " + negociationPrice);

								
								myAgent.send(refus);
								myAgent.send(proposition);

								
								// on attend la reponse (ACCEPT ou REJECT)
								repliesCnt--;
							}
						} else { // 2eme proposal (final)
							if (price < sellerBookPriceNegociation.get(reply.getSender(), book))
								System.err.println(getLocalName() + "[Negociation]: La proposition final a été reçu pour book: " + book + " de "
										+ reply.getSender().getLocalName() + ", prix de negociation: "
										+ sellerBookPriceNegociation.get(reply.getSender(), book) + " nouveau prix proposé" + price);
							else
								System.err.println(getLocalName() + "[Negociation]: La proposition final a été reçu pour book: " + book + " de "
										+ reply.getSender().getLocalName() + ", prix :(le meme prix) " + price);

						}

						if (bookBestPrice.get(book) == null) {
							// book n existe pas on l ajoute
							bookBestPrice.put(book, price);
							bookBestSeller.put(book, reply.getSender());
						} else if (price < bookBestPrice.get(book)) {
							// on a trouve a seller moin chere pour book
							bookBestPrice.put(book, price);
							bookBestSeller.put(book, reply.getSender());
						}

					} else if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
						String txt = reply.getContent();
						Scanner scanner = new Scanner(txt);
						scanner.useDelimiter(",");
						String book = scanner.next();
						//on recupere le prix accepté (negocié)
						int price = sellerBookPriceNegociation.get(reply.getSender(), book); 

						System.err.println(getLocalName() + "[Negociation]: Negociations ont réussie! L'agent " + reply.getSender().getLocalName()
								+ " a accepté le prix: " + price + " pour le book: " + book);

						if (price < bookBestPrice.get(book)) {
							// on a trouve a seller moin chere pour book
							bookBestPrice.put(book, price);
							bookBestSeller.put(book, reply.getSender());
						}
						//seller a refusé notrre offre, on attend une autre proposal
					} else if (reply.getPerformative() == ACLMessage.REJECT_PROPOSAL) { 
						
						String txt = reply.getContent();
						Scanner scanner = new Scanner(txt);
						scanner.useDelimiter(",");
						String book = scanner.next();
						int price = scanner.nextInt();

						System.err.println(getLocalName() + "[Negociation]: Negociations ont echoué! La proposition envoyé a été rejeté pour le book: " + book
								+ " de " + reply.getSender().getLocalName() + " prix: " + sellerBookPriceNegociation.get(reply.getSender(), book));

						repliesCnt--;
					}
					repliesCnt++;
				} else {
//					 System.out.println(getLocalName()+" etape2 blocké");
					block();
				}
				// chaque seller renvoie n fois de proposal,n=nombre de book
				if (repliesCnt >= sellerAgents.length * books.size()) {
					// We received all replies
					// System.out.println(getLocalName()+" 2->3");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					step = 3;
				}
				break;
			case 3:
				// test si on a des offres positives pr tous les livres (on'a
				// trouvé tous les livres)
				if (bookBestSeller.size() == books.size()) {
					System.out.println(getLocalName() + "  ETAPE3: Tous les book ont été trouvés");
					
					int prixAchatTotal = 0;
					for (String	book : bookBestPrice.keySet()) {
						prixAchatTotal += bookBestPrice.get(book);
					}
					
					if(plafond==0)	// 0 = plafond infinie, alors on va explicitement fair majorer le prix d achat total pour passer la condition si desous :p 
						plafond = prixAchatTotal +1;
					
					//on compare avec le plafond
					if(prixAchatTotal <= plafond){
						System.out.println(getLocalName() + "  ETAPE3: prixAchatTotal <= plafond, essayons de les acheter!, plafond-prixAchatTotal="+(plafond-prixAchatTotal));
						step = 4;
					}else{
						System.out.println(getLocalName() + "  ETAPE3: prixAchatTotal > plafond, on peut pas les acheter, arretons nous!!");
						myAgent.doDelete();
						System.out.println(getLocalName() + " --FIN--");
						isFinished = true;
					}
					
					
				} else {
					System.out.println(getLocalName() + "  ETAPE3: Tous les book n'ont pas été trouvés (" + bookBestPrice.size() + " trouvé sur "
							+ books.size() + "). essayons a nouveau!");
					// on revient pr trouvé d autres seller peut etre :p
					step = 0;
					System.out.println(getLocalName() + " 3: PAUSE de 10s");
					
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
				System.out.println(getLocalName() + " 4: Envoie des achats proposals*******");
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

				// PAUSE avant de recevoir
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				repliesCnt = 0;
				step = 5;
				// System.out.println(getLocalName()+" 4->5");
				break;
			case 5:
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				// System.out.println(getLocalName()+"  5");

				if (reply != null) {

					String titre = reply.getContent();
					boolean flag = true;

					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						repliesCnt++;
						// Purchase successful. We can terminate
						System.out.println(getLocalName() + " " + titre + " successfully purchased from agent " + reply.getSender().getLocalName());
						System.out.println(getLocalName() + " Price = " + bookBestPrice.get(titre));

					} else if (reply.getPerformative() == ACLMessage.FAILURE) {
						System.out.println(getLocalName() + " Attempt failed: requested book already sold.");
						myAgent.doDelete();
						System.out.println(getLocalName() + " --FIN--");
						isFinished = true;
					}

					if (repliesCnt == books.size()) {
						if (flag == true)
							System.out.println(getLocalName() + " ********Tous les books ont été achetés********");
						else
							System.out.println(getLocalName() + " ********Tous les book n'ont pas été achetés!!!********");

						myAgent.doDelete();
						System.out.println(getLocalName() + " --FIN--");
						isFinished = true;
					}

				} else {
					// System.out.println(getLocalName()+" ETAPE 5 blocké");
					block();
				}
				break;
			}
		}

		@Override
		public boolean done() {
			return isFinished;
		}

		public int randInt(int min, int max) {

			int randomNum = rand.nextInt((max - min) + 1) + min;

			return randomNum;
		}

	}

}
