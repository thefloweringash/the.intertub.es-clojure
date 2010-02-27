--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = off;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET escape_string_warning = off;

SET search_path = public, pg_catalog;

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: hits; Type: TABLE; Schema: public; Owner: lorne; Tablespace: 
--

CREATE TABLE hits (
    "timestamp" timestamp with time zone DEFAULT now() NOT NULL,
    ip character varying(40) NOT NULL,
    referer text,
    slug character(6) NOT NULL
);


ALTER TABLE public.hits OWNER TO lorne;

--
-- Name: links; Type: TABLE; Schema: public; Owner: lorne; Tablespace: 
--

CREATE TABLE links (
    slug character(6) NOT NULL,
    url character varying(1024) NOT NULL,
    create_date timestamp with time zone DEFAULT now() NOT NULL,
    creator character varying(40) NOT NULL
);


ALTER TABLE public.links OWNER TO lorne;

--
-- Name: name_pkey; Type: CONSTRAINT; Schema: public; Owner: lorne; Tablespace: 
--

ALTER TABLE ONLY links
    ADD CONSTRAINT name_pkey PRIMARY KEY (slug);


--
-- Name: url_uninque; Type: CONSTRAINT; Schema: public; Owner: lorne; Tablespace: 
--

ALTER TABLE ONLY links
    ADD CONSTRAINT url_uninque UNIQUE (url);


--
-- Name: hits_slug_fkey; Type: FK CONSTRAINT; Schema: public; Owner: lorne
--

ALTER TABLE ONLY hits
    ADD CONSTRAINT hits_slug_fkey FOREIGN KEY (slug) REFERENCES links(slug);


--
-- Name: public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

