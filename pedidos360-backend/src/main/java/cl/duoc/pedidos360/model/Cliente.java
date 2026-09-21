package cl.duoc.pedidos360.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cliente que realiza pedidos. Se crea automáticamente la primera vez que un usuario
 * autenticado con Microsoft Entra ID usa la API, con los datos de su token.
 */
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador único del usuario en Entra ID (claim "oid"). Enlaza el cliente con la cuenta Microsoft. */
    @Column(unique = true)
    private String oid;

    @Column(nullable = false)
    private String nombre;

    private String email;

    public Cliente() {}

    public Cliente(String oid, String nombre, String email) {
        this.oid = oid;
        this.nombre = nombre;
        this.email = email;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOid() { return oid; }
    public void setOid(String oid) { this.oid = oid; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
