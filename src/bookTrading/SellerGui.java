package bookTrading;

import jade.core.AID;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

/**
  @author Giovanni Caire - TILAB
 */
class SellerGui extends JFrame {	
	private Seller myAgent;
	
	private JTextField titleField, priceField, nombre;
	
	SellerGui(Seller a) {
		super(a.getLocalName());
		
		myAgent = a;
		
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(3, 2));
		p.add(new JLabel("Titre:"));
		titleField = new JTextField(15);
		p.add(titleField);
		p.add(new JLabel("Prix:"));
		priceField = new JTextField(15);
		p.add(priceField);
		p.add(new JLabel("Nombre:"));
		nombre = new JTextField(15);
		nombre.setText("1");
		p.add(nombre);
		getContentPane().add(p, BorderLayout.CENTER);
		
		JButton addButton = new JButton("Add");
		addButton.addActionListener( new ActionListener() {
			public void actionPerformed(ActionEvent ev) {
				try {
					String title = titleField.getText().trim();
					String price = priceField.getText().trim();
					String nbr = nombre.getText().trim();
					myAgent.updateCatalogue(title, Integer.parseInt(price), Integer.parseInt(nbr));
					titleField.setText("");
					priceField.setText("");
					nombre.setText("1");
				}
				catch (Exception e) {
					JOptionPane.showMessageDialog(SellerGui.this, "Invalid values. "+e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); 
				}
			}
		} );
		p = new JPanel();
		p.add(addButton);
		getContentPane().add(p, BorderLayout.SOUTH);
		
		// Make the agent terminate when the user closes 
		// the GUI using the button on the upper right corner	
		addWindowListener(new	WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				myAgent.doDelete();
			}
		} );
		
		setResizable(false);
	}
	
	public void show() {
		pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int centerX = (int)screenSize.getWidth() / 2;
		int centerY = (int)screenSize.getHeight() / 2;
		setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
		super.show();
	}	
}
