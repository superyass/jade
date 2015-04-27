package bookTrading;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;
import java.util.Scanner;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class SellerNegotiator extends Agent {

	private Hashtable<String, Integer> bookPrix;
	private Hashtable<String, Integer> bookNombre;
	private Hashtable<String, Integer> bookProfit;
	private SellerGuiNegotiator myGui;
	
	Table<AID, String, Integer> buyerBookPriceNegociation = HashBasedTable.create();	
	
	@Override
	protected void setup() {
		

		bookPrix = new Hashtable<String, Integer>();
		bookNombre = new Hashtable<String, Integer>();
		bookProfit = new Hashtable<String, Integer>();

		myGui = new SellerGuiNegotiator(this);
		myGui.show();

		// Register the book-selling service in the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("book-selling");
		sd.setName("JADE-book-trading");
		dfd.addServices(sd);
		
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}

		// Add the behaviour serving queries from buyer agents
		addBehaviour(new OfferRequestsServer());
		// Add the behaviour serving purchase orders from buyer agents
		addBehaviour(new PurchaseOrdersServer());
		
		addBehaviour(new Negotiation());
	}
	
	private class OfferRequestsServer extends CyclicBehaviour {

		public synchronized void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
			ACLMessage msg = myAgent.receive(mt);
			
			if (msg != null) {
				// CFP Message received. Process it
				// get contenu, bzaaf 
				String titles = msg.getContent();
				// get books delimited by , from contenu :p
				ArrayList<String> books = new ArrayList<String>();
				Scanner scanner = new Scanner(titles);
				scanner.useDelimiter(",");
				while (scanner.hasNext()) {
					books.add(scanner.next());
				}

				// repondre
				for (String book : books) {
					ACLMessage cfp = new ACLMessage();
					cfp.addReceiver(msg.getSender());
					cfp.setConversationId("negociation");

					Integer price = bookPrix.get(book);
					if (price != null) {
						// The requested book is available for sale. Reply with
						// the price
						cfp.setPerformative(ACLMessage.PROPOSE);
						cfp.setConversationId("negociation");
						cfp.setContent(book + "," + String.valueOf(price.intValue()));
						System.err.println(getLocalName()+"[Negociation]: envoie d'une premiere proposition a  "+msg.getSender().getLocalName()+" ,le prix: "+bookPrix.get(book)+" pour le book: "+book);
					} else {
						// The requested book is NOT available for sale.
						cfp.setPerformative(ACLMessage.REFUSE);
						cfp.setContent(book + " not-available");
					}
					myAgent.send(cfp);
				}

			} else {
				block();
			}
		}
	} // End of inner class OfferRequestsServer
	
	private class Negotiation extends CyclicBehaviour {
		
		Random rand = new Random();

		public synchronized void action() {
			MessageTemplate mt = MessageTemplate.MatchConversationId("negociation");
			ACLMessage msg = myAgent.receive(mt);
			
			if (msg != null) {
				
				String txt = msg.getContent();
				Scanner scanner = new Scanner(txt);
				scanner.useDelimiter(",");
				String book = scanner.next();
				int price = scanner.nextInt();
				
				if(msg.getPerformative() == ACLMessage.REJECT_PROPOSAL){
					
					System.err.println(getLocalName()+"[Negociation]: le Buyer: "+msg.getSender().getLocalName()+" a refusé le prix: "+price+" pour le book: "+book);
					
				}else if(msg.getPerformative() == ACLMessage.PROPOSE){
					
					int profit = (int) (bookProfit.get(book)/100);
					int prixFinal = randInt(bookPrix.get(book) - (bookPrix.get(book) * profit ), bookPrix.get(book));
					
					//si le prix proposé est inferieur au prix final generé randomly
					if(price < prixFinal ){	
						
						// on evoie un refus puis une proposition
						ACLMessage refus = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
						refus.addReceiver(msg.getSender());
						refus.setConversationId("negociation");
						refus.setContent(book + "," + price);
						System.err.println(getLocalName()+"[Negociation]: rejet de la  proposition de "+msg.getSender().getLocalName()+" ,pour le prix: "+price+" pour le book: "+book);
						

						ACLMessage propose = new ACLMessage(ACLMessage.PROPOSE);
						propose.addReceiver(msg.getSender());
						propose.setConversationId("negociation");
						propose.setContent(book + "," + prixFinal);
						
						myAgent.send(refus);
						myAgent.send(propose);
						
						System.err.println(getLocalName()+"[Negociation]: envoie du nouvelle proposition a "+msg.getSender().getLocalName()+" ,le prix: "+prixFinal+" pour le book: "+book);
						
					}else{
						
						ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
						accept.addReceiver(msg.getSender());
						accept.setConversationId("negociation");
						accept.setContent(book + "," + price);
						myAgent.send(accept);
						System.err.println(getLocalName()+"[Negociation]: Proposition accepté de "+msg.getSender().getLocalName()+" ,pour le prix: "+price+" pour le book: "+book);
						
					}
					
				}
				
			} else {
				block();
			}
			
		}
		
		public int randInt(int min, int max) {

			int randomNum = rand.nextInt((max - min) + 1) + min;

			return randomNum;
		}
	}

	private class PurchaseOrdersServer extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			
			if (msg != null) {
				// ACCEPT_PROPOSAL Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();
				reply.setContent(title);

				Integer nombre = bookNombre.get(title);

				if (nombre != 0) {
					reply.setPerformative(ACLMessage.INFORM);
					bookNombre.put(title, --nombre);
					if (bookNombre.get(title) == 0) {
						bookPrix.remove(title);
					}
					System.err.println(getLocalName() + ": " + title + " vendu a " + msg.getSender().getLocalName() + " exemplaire restant: "
							+ bookNombre.get(title));
				} else {
					// The requested book has been sold to another buyer in the
					// meanwhile .
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent(getLocalName() + ": "+title + " not-available");
				}
				myAgent.send(reply);
				
				if(bookPrix.size()==0){
					System.err.println(getLocalName()+": Tous les livres ont été vendus");
					System.err.println(getLocalName()+" --FIN--");
					myGui.dispose();
					doDelete();
				}
			} else {
				block();
			}
		}
	} // End of inner class OfferRequestsServer

	public void updateCatalogue(final String title, final int price, final int nombre, final int profit) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				bookPrix.put(title, new Integer(price)); //s'il existe déja on ecrase l'ancien prix
				bookProfit.put(title, profit);
				
				Integer ancienNombreExemplaires = bookNombre.get(title);
				if(ancienNombreExemplaires==null){	//le book n'existe pas deja
					ancienNombreExemplaires=new Integer(0);
					System.err.println(getLocalName() + ": "+title + " a été inséré. Prix = " + price + " exemplaires: " + nombre);
				}else{
					System.err.println(getLocalName() + ": "+title + " existe deja.le nouveau Prix = " + price + " Total des exemplaires: " + (nombre+ancienNombreExemplaires));
				}
				bookNombre.put(title, new Integer(nombre+ancienNombreExemplaires));
				
			}
		});
	}

}
