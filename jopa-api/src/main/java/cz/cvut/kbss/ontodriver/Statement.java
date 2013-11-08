package cz.cvut.kbss.ontodriver;

import java.net.URI;

import cz.cvut.kbss.ontodriver.exceptions.OntoDriverException;

/**
 * This interface represents a SPARQL statement.
 * 
 * @author kidney
 * 
 */
public interface Statement {

	/**
	 * Execute the specified SPARQL query.
	 * 
	 * @param sparql
	 *            The statement to execute
	 * @param contextUri
	 *            URI of the ontology context against which the query should be
	 *            evaluated
	 * @return {@code ResultSet} containing results of the query
	 * @throws OntoDriverException
	 *             If an error occurs during query execution
	 */
	public ResultSet executeQuery(String sparql, URI contextUri) throws OntoDriverException;

	/**
	 * Execute the specified SPARQL update query. </p>
	 * 
	 * The return value is optional and implementations may choose to return 0
	 * by default.
	 * 
	 * @param sparql
	 *            The statement to execute
	 * @param contextUri
	 *            URI of the ontology context against which the query should be
	 *            evaluated
	 * @return Number of affected axioms
	 * @throws OntoDriverException
	 *             If an error occurs during query execution
	 */
	public int executeUpdate(String sparql, URI contextUri) throws OntoDriverException;

	/**
	 * Use the transactional ontology for query processing. </p>
	 * 
	 * Using the transactional ontology can produce different results than using
	 * the central (backup) ontology, since the transactional ontology can
	 * contain uncommitted changes from the current transaction. </p>
	 * 
	 * This is default behavior.
	 * 
	 * @see #setUseBackupOntology()
	 */
	public void setUseTransactionalOntology();

	/**
	 * Returns true if the transactional ontology should be used for query
	 * processing.
	 * 
	 * @return boolean
	 */
	public boolean useTransactionalOntology();

	/**
	 * Use the backup (central) ontology for query processing.
	 * 
	 * @see #useTransactionalOntology()
	 */
	public void setUseBackupOntology();

	/**
	 * Returns true if the backup (central) ontology should be used for query
	 * processing.
	 * 
	 * @return boolean
	 */
	public boolean useBackupOntology();
}
