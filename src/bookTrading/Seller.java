package bookTrading;

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
import java.util.Scanner;

public class Seller extends Agent {

	private Hashtable<String, Integer> bookPrix;
	private Hashtable<String, Integer> bookNombre;
	private SellerGui myGui;

	@Override
	protected void setup() {
		

		bookPrix = new Hashtable<String, Integer>();
		bookNombre = new Hashtable<String, Integer>();

		myGui = new SellerGui(this);
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
					ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
					cfp.addReceiver(msg.getSender());
					cfp.setConversationId("book-trade");

					Integer price = bookPrix.get(book);
					if (price != null) {
						// The requested book is available for sale. Reply with
						// the price
						cfp.setPerformative(ACLMessage.PROPOSE);
						cfp.setContent(book + "," + String.valueOf(price.intValue()));
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

	public void updateCatalogue(final String title, final int price, final int nombre) {
		addBehaviour(new OneShotBehaviour() {
			public void action() {
				bookPrix.put(title, new Integer(price)); //s'il existe déja on ecrase l'ancien prix
				
				Integer ancienNombreExemplaires = bookNombre.get(title);
				if(ancienNombreExemplaires==null){	//le book n'existe pas deja
					ancienNombreExemplaires=new Integer(0);
				}
				bookNombre.put(title, new Integer(nombre+ancienNombreExemplaires));
				System.err.println(getLocalName() + ": "+title + " a été inséré. Prix = " + price + " Nombre: " + nombre);
			}
		});
	}

}
