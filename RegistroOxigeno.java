import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class RegistroOxigeno extends JFrame {

    private static final String ARCHIVO = "registro_oxigeno.csv";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final List<Registro> registros = new ArrayList<>();
    private DefaultTableModel modelo;

    private final JTextField txtDni = new JTextField(12);
    private final JTextField txtNombre = new JTextField(20);
    private final JTextField txtEdad = new JTextField(4);
    private final JTextField txtSpo2 = new JTextField(4);
    private final JTextField txtFrecResp = new JTextField(4);
    private final JComboBox<String> cmbTipo = new JComboBox<>(new String[]{
            "Cánula nasal", "Mascarilla simple", "Mascarilla reservorio", "Casco hood", "Alto flujo (CNAF)"});
    private final JTextArea txtObservaciones = new JTextArea(3, 30);
    private JTable tabla;

    public RegistroOxigeno() {
        super("Registro de Oxígeno - Hospital Chulucanas");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(12, 12, 12, 12));

        add(crearPanelFormulario(), BorderLayout.NORTH);
        add(crearPanelTabla(), BorderLayout.CENTER);
        add(crearPanelBotones(), BorderLayout.SOUTH);

        cargarRegistros();
        actualizarTabla();
        txtNombre.requestFocusInWindow();

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel crearPanelFormulario() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Datos del paciente"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;

        int fila = 0;
        agregarEtiqueta(panel, g, "DNI:", 0, fila);
        g.gridx = 1;
        panel.add(txtDni, g);
        agregarEtiqueta(panel, g, "Nombre completo:", 2, fila);
        g.gridx = 3;
        panel.add(txtNombre, g);
        fila++;

        agregarEtiqueta(panel, g, "Edad:", 0, fila);
        g.gridx = 1;
        panel.add(txtEdad, g);
        agregarEtiqueta(panel, g, "SpO2 (%):", 2, fila);
        g.gridx = 3;
        panel.add(txtSpo2, g);
        fila++;

        agregarEtiqueta(panel, g, "Frecuencia respiratoria (rpm):", 0, fila);
        g.gridx = 1;
        panel.add(txtFrecResp, g);
        agregarEtiqueta(panel, g, "Tipo de oxígeno:", 2, fila);
        g.gridx = 3;
        panel.add(cmbTipo, g);
        fila++;

        agregarEtiqueta(panel, g, "Observaciones:", 0, fila);
        g.gridx = 1;
        g.gridwidth = 3;
        txtObservaciones.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        panel.add(new JScrollPane(txtObservaciones), g);
        g.gridwidth = 1;

        return panel;
    }

    private void agregarEtiqueta(JPanel panel, GridBagConstraints g, String texto, int x, int fila) {
        g.gridx = x;
        g.gridy = fila;
        panel.add(new JLabel(texto), g);
    }

    private JPanel crearPanelTabla() {
        modelo = new DefaultTableModel(new String[]{
                "DNI", "Nombre", "Edad", "SpO2 %", "FR rpm", "Tipo de oxígeno", "Fecha", "Estado", "Observaciones"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        tabla.setAutoCreateRowSorter(true);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(180);
        tabla.getColumnModel().getColumn(5).setPreferredWidth(140);
        tabla.getColumnModel().getColumn(6).setPreferredWidth(110);
        tabla.getColumnModel().getColumn(7).setPreferredWidth(120);

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setPreferredSize(new Dimension(950, 260));
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("Pacientes registrados"));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelBotones() {
        JButton btnRegistrar = new JButton("Registrar paciente");
        btnRegistrar.addActionListener(e -> registrar());

        JButton btnEliminar = new JButton("Eliminar seleccionado");
        btnEliminar.addActionListener(e -> eliminar());

        JButton btnLimpiar = new JButton("Limpiar formulario");
        btnLimpiar.addActionListener(e -> limpiar());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        panel.add(btnRegistrar);
        panel.add(btnEliminar);
        panel.add(btnLimpiar);
        return panel;
    }

    private void registrar() {
        String dni = txtDni.getText().trim();
        String nombre = txtNombre.getText().trim();
        String edadS = txtEdad.getText().trim();
        String spo2S = txtSpo2.getText().trim();
        String frS = txtFrecResp.getText().trim();

        if (dni.isEmpty() || nombre.isEmpty() || edadS.isEmpty() || spo2S.isEmpty() || frS.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Complete DNI, nombre, edad, SpO2 y frecuencia respiratoria.",
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int edad, spo2, fr;
        try {
            edad = Integer.parseInt(edadS);
            spo2 = Integer.parseInt(spo2S);
            fr = Integer.parseInt(frS);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Edad, SpO2 y frecuencia respiratoria deben ser números.",
                    "Dato inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (edad < 0 || edad > 120) {
            JOptionPane.showMessageDialog(this, "Edad no válida.", "Dato inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (spo2 < 50 || spo2 > 100) {
            JOptionPane.showMessageDialog(this, "SpO2 debe estar entre 50 y 100.", "Dato inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (fr < 5 || fr > 80) {
            JOptionPane.showMessageDialog(this, "Frecuencia respiratoria debe estar entre 5 y 80.", "Dato inválido", JOptionPane.ERROR_MESSAGE);
            return;
        }

        registros.add(new Registro(dni, nombre, edad, spo2, fr,
                (String) cmbTipo.getSelectedItem(),
                LocalDateTime.now().format(FECHA),
                estadoSpo2(spo2),
                txtObservaciones.getText().trim()));

        guardarRegistros();
        actualizarTabla();
        limpiar();
        JOptionPane.showMessageDialog(this, "Paciente registrado correctamente.", "Éxito", JOptionPane.INFORMATION_MESSAGE);
    }

    private String estadoSpo2(int spo2) {
        if (spo2 >= 95) return "Normal";
        if (spo2 >= 90) return "Hipoxemia leve";
        if (spo2 >= 80) return "Hipoxemia moderada";
        return "Hipoxemia grave";
    }

    private void eliminar() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un registro de la tabla.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int respuesta = JOptionPane.showConfirmDialog(this, "¿Eliminar el registro seleccionado?", "Confirmar",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (respuesta == JOptionPane.YES_OPTION) {
            registros.remove(tabla.convertRowIndexToModel(fila));
            guardarRegistros();
            actualizarTabla();
        }
    }

    private void limpiar() {
        txtDni.setText("");
        txtNombre.setText("");
        txtEdad.setText("");
        txtSpo2.setText("");
        txtFrecResp.setText("");
        txtObservaciones.setText("");
        cmbTipo.setSelectedIndex(0);
        txtDni.requestFocusInWindow();
    }

    private void actualizarTabla() {
        modelo.setRowCount(0);
        for (Registro r : registros) {
            modelo.addRow(new Object[]{
                    r.dni, r.nombre, r.edad, r.spo2, r.frecResp, r.tipo, r.fecha, r.estado, r.observaciones});
        }
    }

    private void cargarRegistros() {
        File f = new File(ARCHIVO);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] p = linea.split(";");
                if (p.length == 9) {
                    registros.add(new Registro(p[0], p[1], Integer.parseInt(p[2]),
                            Integer.parseInt(p[3]), Integer.parseInt(p[4]), p[5], p[6], p[7], p[8]));
                }
            }
        } catch (IOException | NumberFormatException ex) {
            System.err.println("No se pudo leer el archivo de registros: " + ex.getMessage());
        }
    }

    private void guardarRegistros() {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(ARCHIVO), StandardCharsets.UTF_8))) {
            for (Registro r : registros) {
                bw.write(String.join(";", r.dni, r.nombre, String.valueOf(r.edad),
                        String.valueOf(r.spo2), String.valueOf(r.frecResp), r.tipo, r.fecha, r.estado, r.observaciones));
                bw.newLine();
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static class Registro {
        final String dni, nombre, tipo, fecha, estado, observaciones;
        final int edad, spo2, frecResp;

        Registro(String dni, String nombre, int edad, int spo2, int frecResp,
                 String tipo, String fecha, String estado, String observaciones) {
            this.dni = dni;
            this.nombre = nombre;
            this.edad = edad;
            this.spo2 = spo2;
            this.frecResp = frecResp;
            this.tipo = tipo;
            this.fecha = fecha;
            this.estado = estado;
            this.observaciones = observaciones;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RegistroOxigeno::new);
    }
}
